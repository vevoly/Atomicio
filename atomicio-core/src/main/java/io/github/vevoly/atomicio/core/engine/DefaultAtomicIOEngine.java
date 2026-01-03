package io.github.vevoly.atomicio.core.engine;

import io.github.vevoly.atomicio.api.*;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterMessageType;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.api.config.AtomicIOEngineConfig;
import io.github.vevoly.atomicio.api.constants.AtomicIOConstant;
import io.github.vevoly.atomicio.api.constants.IdleState;
import io.github.vevoly.atomicio.api.listeners.*;
import io.github.vevoly.atomicio.core.event.DisruptorManager;
import io.github.vevoly.atomicio.core.protocol.TextMessageDecoder;
import io.github.vevoly.atomicio.core.protocol.TextMessageEncoder;
import io.github.vevoly.atomicio.core.session.NettySession;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;
import reactor.util.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AtomicIOEngine 的默认实现。
 * 负责启动和管理 Netty 服务器，以及所有会话的生命周期。
 *
 * @since 0.0.1
 * @author vevoly
 */
@Slf4j
public class DefaultAtomicIOEngine implements AtomicIOEngine {

    private final AtomicIOEngineConfig config;
    private final AtomicIOClusterProvider clusterProvider;

    // Netty核心组件
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture serverChannelFuture;
    private final DisruptorManager disruptorManager = new DisruptorManager(); // Disruptor 管理器

    // 存储监听器集合
    private final List<ConnectEventListener> connectEventListeners = new CopyOnWriteArrayList<>();
    private final List<DisconnectEventListener> disconnectEventListeners = new CopyOnWriteArrayList<>();
    private final List<MessageEventListener> messageListeners = new CopyOnWriteArrayList<>();
    private final List<ErrorEventListener> errorListeners = new CopyOnWriteArrayList<>();
    private final List<IdleEventListener> idleEventListeners = new CopyOnWriteArrayList<>();

    // 映射表
    private final Map<String, AtomicIOSession> userIdToSessionMap = new ConcurrentHashMap<>(); // Key: 用户ID, Value: 对应的会话
    private final Map<String, String> sessionIdToUserIdMap = new ConcurrentHashMap<>(); // Key: 会话ID, Value: 对应的用户ID
    private final Map<String, ChannelGroup> groups = new ConcurrentHashMap<>(); // Key: 组ID, Value: 组内的所有会话

    // 线程安全的状态机
    private final AtomicReference<AtomicIOLifeState> state = new AtomicReference<>(AtomicIOLifeState.NEW);

    public DefaultAtomicIOEngine(AtomicIOEngineConfig config, @Nullable AtomicIOClusterProvider clusterProvider) {
        this.config = Objects.requireNonNull(config, "EngineConfig cannot be null");
        this.clusterProvider = clusterProvider;
    }

    /**
     * 实现 AtomicIOEngine 接口方法
     * @return
     */
    @Override
    public Future<Void> start() {
        CompletableFuture<Void> startFuture = new CompletableFuture<>();
        if (!state.compareAndSet(AtomicIOLifeState.NEW, AtomicIOLifeState.STARTING)) {
            log.warn("Engine cannot be started from state {}", state.get());
            startFuture.completeExceptionally(new IllegalStateException("Engine is not in a startable state."));
            return startFuture;
        }

        try {
            log.info("Atomicio Engine is starting...");
            doStart();
            state.set(AtomicIOLifeState.RUNNING);
            log.info("Atomicio Engine started successfully.");
            startFuture.complete(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            state.set(AtomicIOLifeState.SHUTDOWN);
            doStop();
            startFuture.completeExceptionally(new RuntimeException("Engine start failed", e));
        }
        return startFuture;
    }

    @Override
    public void shutdown() {
        doStop();
    }

    /**
     * 核心启动逻辑，供 start() 和 LifecycleManager 调用。
     * @throws InterruptedException 如果启动被中断
     */
    void doStart() throws InterruptedException {
        // 初始化 Netty 线程组
        bossGroup = new NioEventLoopGroup(config.getBossThreads());
        workerGroup = new NioEventLoopGroup(config.getWorkerThreads());
        // 启动 Disruptor
        disruptorManager.start(this);
        if (this.clusterProvider != null) { // 启动集群
            clusterProvider.start();
            // 订阅消息，并将收到的消息发送到 Disruptor 队列
            clusterProvider.subscribe(atomicIOClusterMessage -> {
                disruptorManager.publishClusterEvent(atomicIOClusterMessage);
            });
        }

        // 启动 Netty 服务器
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    // 每当有一个新的连接被 bossGroup 接受后，就初始化这个新连接的 ChannelPipeline
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        // 定义 ChannelPipeline
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        // 添加编码器，因为它只处理出站消息
                        pipeline.addLast(AtomicIOConstant.PIPELINE_NAME_ENCODER, new TextMessageEncoder());
                        // 帧解码器 (Frame Decoder): 解决 TCP 粘包/半包问题
                        pipeline.addLast(AtomicIOConstant.PIPELINE_NAME_FRAME_DECODER, new LineBasedFrameDecoder(1024));
                        // 消息解码器 (Message Decoder)
                        pipeline.addLast(AtomicIOConstant.PIPELINE_NAME_DECODER, new TextMessageDecoder());
                        // 心跳检测
                        pipeline.addLast(AtomicIOConstant.PIPELINE_NAME_IDLE_STATE_HANDLER,
                                new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS));
                        // 核心处理器，事件翻译
                        pipeline.addLast(AtomicIOConstant.PIPELINE_NAME_CHANNEL_HANDLER, new EngineChannelHandler(disruptorManager, DefaultAtomicIOEngine.this));
                    }
                });
        serverChannelFuture = bootstrap.bind(config.getPort()).sync(); // 绑定端口
        log.info("Atomicio Engine bound successfully to port {}.", config.getPort());
    }

    /**
     * 核心关闭逻辑，供 shutdown() 和 LifecycleManager 调用。
     */
    void doStop() {
        if (!state.compareAndSet(AtomicIOLifeState.RUNNING, AtomicIOLifeState.SHUTTING_DOWN)) {
            // 如果引擎从未运行过，或者正在关闭/已关闭，则直接返回
            log.warn("Engine is not running or already shutting down. Current state: {}", state.get());
            return;
        }
        log.info("Atomicio Engine shutting down...");
        // 关闭顺序与启动相反
        if (serverChannelFuture != null) {
            serverChannelFuture.channel().close().syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (this.clusterProvider != null) {
            clusterProvider.shutdown();
        }
        disruptorManager.shutdown();

        state.set(AtomicIOLifeState.SHUTDOWN);
        log.info("Atomicio Engine shutdown gracefully.");
    }

    /**
     * 检查引擎是否正在运行。
     * @return true 如果引擎处于 RUNNING 状态
     */
    public boolean isRunning() {
        return state.get() == AtomicIOLifeState.RUNNING;
    }

    @Override
    public void onConnect(ConnectEventListener listener) {
        this.connectEventListeners.add(listener);
    }

    @Override
    public void onDisconnect(DisconnectEventListener listener) {
        this.disconnectEventListeners.add(listener);
    }

    @Override
    public void onMessage(MessageEventListener listener) {
        this.messageListeners.add(listener);
    }

    @Override
    public void onError(ErrorEventListener listener) {
        this.errorListeners.add(listener);
    }

    @Override
    public void onIdle(IdleEventListener listener) {
        this.idleEventListeners.add(listener);
    }


    @Override
    public void bindUser(String userId, AtomicIOSession session) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(session, "Session cannot be null");

        // 1. 先进行清理：如果这个 Session 之前绑定过其他用户，或者这个用户之前绑定过其他 Session
        String oldUserId = sessionIdToUserIdMap.put(session.getId(), userId);
        if (oldUserId != null && !oldUserId.equals(userId)) {
            // 同一个 Session 绑定了新用户，移除旧用户的关联
            userIdToSessionMap.remove(oldUserId);
            log.warn("Session {} re-bound from user {} to user {}", session.getId(), oldUserId, userId);
        }
        AtomicIOSession oldSession = userIdToSessionMap.put(userId, session);
        if (oldSession != null && !oldSession.getId().equals(session.getId())) {
            // 同一个用户在其他 Session 上登录了，旧的 Session 失效
            log.warn("User {} re-logged in. Old session {} will be closed.", userId, oldSession.getId());
            oldSession.close(); // 强制关闭旧的连接
            // 同时清理旧 Session 的反向映射
            sessionIdToUserIdMap.remove(oldSession.getId());
        }

        // 2. 将用户ID存储在 Session 属性中，方便 Session 内部访问
        session.setAttribute("userId", userId);
        session.setAttribute("isAuthenticated", true); // 标记为已认证
        log.info("User {} bound to session {}", userId, session.getId());
    }

    @Override
    public void sendToUser(String userId, AtomicIOMessage message) {
        boolean sentLocally = sendToUserLocally(userId, message);
        if (!sentLocally && clusterProvider != null) {
            AtomicIOClusterMessage clusterMessage = new AtomicIOClusterMessage();
            clusterMessage.setMessageType(AtomicIOClusterMessageType.SEND_TO_USER);
            clusterMessage.setTarget(userId);
            clusterMessage.setOriginalMessage(message);
            clusterProvider.publish(clusterMessage);
        }
    }

    /**
     * 只在本地发送消息给用户
     * 这个方法由集群消息处理器调用
     * @param userId    用户 ID
     * @param message   消息
     */
    boolean sendToUserLocally(String userId, AtomicIOMessage message) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(message, "Message cannot be null");
        AtomicIOSession localSession = userIdToSessionMap.get(userId);
        if (localSession != null && localSession.isActive()) {
            localSession.send(message);
            log.debug("Sent message from cluster to local user {}", userId);
            return true;
        }
        return false;
    }

    @Override
    public void joinGroup(String groupId, String userId) {
        Objects.requireNonNull(groupId, "Group ID cannot be null");
        Objects.requireNonNull(userId, "User ID cannot be null");

        AtomicIOSession session = userIdToSessionMap.get(userId);
        if (session == null || !session.isActive()) {
            log.warn("Cannot join group {}: User {} is not online.", groupId, userId);
            return;
        }

        // 获取或创建 ChannelGroup
        ChannelGroup group = groups.computeIfAbsent(groupId, k -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)); // GlobalEventExecutor.INSTANCE 确保 Group 资源的正确管理
        // 将 NettySession 内部的 Channel 加入到 ChannelGroup
        group.add(((NettySession) session).getNettyChannel());
        // 群组信息存储在 Session 属性中，方便后续清理
        Set<String> userGroups = session.getAttribute("groups");
        if (userGroups == null) {
            userGroups = ConcurrentHashMap.newKeySet(); // 线程安全的 Set
            session.setAttribute("groups", userGroups);
        }
        userGroups.add(groupId);
        log.info("User {} joined group {}. Current group size: {}", userId, groupId, group.size());
    }

    @Override
    public void leaveGroup(String groupId, String userId) {
        Objects.requireNonNull(groupId, "Group ID cannot be null");
        Objects.requireNonNull(userId, "User ID cannot be null");

        AtomicIOSession session = userIdToSessionMap.get(userId);
        if (session == null || !session.isActive()) {
            // 如果用户不在线
            log.debug("User {} is not online, no need to leave group {} actively.", userId, groupId);
            return;
        }

        ChannelGroup group = groups.get(groupId);
        if (group != null) {
            group.remove(((NettySession) session).getNettyChannel());
            // 从 Session 属性中移除群组信息
            Set<String> userGroups = session.getAttribute("groups");
            if (userGroups != null) {
                userGroups.remove(groupId);
            }
            log.info("User {} left group {}. Current group size: {}", userId, groupId, group.size());
            // 如果群组为空，移除 ChannelGroup 实例以节省内存
            if (group.isEmpty()) {
                groups.remove(groupId);
                log.info("Group {} is empty and removed.", groupId);
            }
        } else {
            log.warn("Group {} does not exist for user {}", groupId, userId);
        }
    }

    @Override
    public void sendToGroup(String groupId, AtomicIOMessage message, String... excludeUserIds) {
        sendToGroupLocally(groupId, message, excludeUserIds);
        // 集群模式下，通知其他节点
        if (clusterProvider != null) {
            AtomicIOClusterMessage clusterMessage = new AtomicIOClusterMessage();
            clusterMessage.setMessageType(AtomicIOClusterMessageType.SEND_TO_GROUP);
            clusterMessage.setTarget(groupId);
            clusterMessage.setOriginalMessage(message);
            if (excludeUserIds != null && excludeUserIds.length > 0) {
                clusterMessage.setExcludeUserIds(Set.of(excludeUserIds));
            }
            clusterProvider.publish(clusterMessage);
        }
    }

    /**
     * 只在本地向群组发送消息
     * @param groupId        群组 ID
     * @param message        消息
     * @param excludeUserIds 排除的用户 ID 列表
     */
    void sendToGroupLocally(String groupId, AtomicIOMessage message, String... excludeUserIds) {
        Objects.requireNonNull(groupId, "Group ID cannot be null");
        Objects.requireNonNull(message, "Message cannot be null");

        ChannelGroup group = groups.get(groupId);
        if (group != null && !group.isEmpty()) {
            if (excludeUserIds != null && excludeUserIds.length > 0) {
                // 如果有排除的用户，需要逐个发送，并跳过排除者
                Set<String> excludeSet = Set.of(excludeUserIds);

                // todo 优化点：维护一个 groupId -> Set<userId>的映射，joinGroup 和 leaveGroup 时更新。
                //  allUserIdsInGroup.removeAll(excludeSet) 得到最终需要发送的 userId 列表
                group.forEach(channel -> {
                    AtomicIOSession session = new NettySession(channel, DefaultAtomicIOEngine.this);
                    String userId = session.getAttribute("userId");
                    if (userId != null && !excludeSet.contains(userId)) {
                        session.send(message);
                    }
                });
                log.debug("Sent message {} to group {} excluding {} users.", message.getCommandId(), groupId, excludeUserIds.length);
            } else {
                // 没有排除用户，直接调用 ChannelGroup 的批量发送，性能最高
                group.writeAndFlush(message);
                log.debug("Sent message {} to group {} (total {} sessions).", message.getCommandId(), groupId, group.size());
            }
        } else {
            log.warn("Group {} is empty or does not exist. Message {} not sent.", groupId, message.getCommandId());
        }
    }

    @Override
    public void broadcast(AtomicIOMessage message) {
        broadcastLocally(message);
        // 集群模式下，通知其他节点
        if (clusterProvider != null) {
            AtomicIOClusterMessage clusterMessage = new AtomicIOClusterMessage();
            clusterMessage.setMessageType(AtomicIOClusterMessageType.BROADCAST);
            clusterMessage.setOriginalMessage(message);
            clusterProvider.publish(clusterMessage);
        }
    }

    /**
     * 本地广播
     */
    void broadcastLocally(AtomicIOMessage message) {
        Objects.requireNonNull(message, "Message cannot be null");
        // 遍历所有在线的 Session 并发送
        userIdToSessionMap.values().forEach(session -> {
            if (session.isActive()) {
                session.send(message);
            }
        });
        log.info("Broadcast message {} to all {} online sessions.", message.getCommandId(), userIdToSessionMap.size());
    }

    /**
     * 触发 CONNECT 事件
     */
    void fireConnectEvent(AtomicIOSession session) {
        for (ConnectEventListener listener : connectEventListeners) {
            try {
                listener.onConnected(session);
            } catch (Exception e) {
                log.error("Error executing connect listener for session {}", session.getId(), e);
                fireErrorEvent(session, e);
            }
        }
    }

    /**
     * 触发 DISCONNECT 事件
     */
    void fireDisconnectEvent(AtomicIOSession session) {
        for (DisconnectEventListener listener : disconnectEventListeners) {
            try {
                listener.onDisconnected(session);
            } catch (Exception e) {
                log.error("Error executing disconnect listener for session {}", session.getId(), e);
                fireErrorEvent(session, e);
            }
        }
    }

    /**
     * 触发 MESSAGE 事件
     * @param session   当前会话
     * @param message   收到的消息
     */
    void fireMessageEvent(AtomicIOSession session, AtomicIOMessage message) {
        if (this.messageListeners.isEmpty()) {
            return;
        }
        for (MessageEventListener listener : this.messageListeners) {
            try {
                listener.onMessage(session, message);
            } catch (Exception e) {
                log.error("Error executing message listener for session {}", session.getId(), e);
                fireErrorEvent(session, e);
            }
        }
    }

    /**
     * 触发 ERROR 事件
     * @param session   当前会话
     * @param cause     异常
     */
    void fireErrorEvent(AtomicIOSession session, Throwable cause) {
        if (this.errorListeners.isEmpty()) {
            return;
        }
        for (ErrorEventListener listener : this.errorListeners)
            try {
                listener.onError(session, cause);
            } catch (Exception e) {
                log.error("CRITICAL: Error executing the error listener itself!", e);
            }
    }

    void fireIdleEvent(AtomicIOSession session, IdleState state) {
        if (this.idleEventListeners.isEmpty()) {
            return;
        }
        for (IdleEventListener listener : this.idleEventListeners)
            try {
                listener.onIdle(session, state);
            } catch (Exception e) {
                log.error("Error executing idle listener for session {}", session.getId(), e);
            }
    }


    /**
     * 引擎内部的清理方法：当 Session 断开时，自动解除用户绑定和群组关系。
     * 这个方法不应该暴露给外部，只由 EngineChannelHandler 调用。
     */
    void unbindUserInternal(String userId, AtomicIOSession session) {
        if (userId != null && session != null) {
            // 从用户ID到Session的映射中移除
            userIdToSessionMap.remove(userId, session);
            // 从SessionID到用户ID的映射中移除
            sessionIdToUserIdMap.remove(session.getId());
            // 自动让用户离开所有他加入的群组
            Set<String> userGroups = session.getAttribute("groups");
            if (userGroups != null && !userGroups.isEmpty()) {
                for (String groupId : userGroups) {
                    leaveGroup(groupId, userId); // 调用 leaveGroup，内部会清理 ChannelGroup
                }
                session.setAttribute("groups", null); // 清理 Session 属性
            }
            log.info("User {} (session {}) automatically unbound and left all groups due to disconnect.", userId, session.getId());
        }
    }

}
