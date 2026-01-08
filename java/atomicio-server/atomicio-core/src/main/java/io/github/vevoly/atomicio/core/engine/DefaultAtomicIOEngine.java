package io.github.vevoly.atomicio.core.engine;

import io.github.vevoly.atomicio.api.*;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterMessageType;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.api.codec.AtomicIOCodecProvider;
import io.github.vevoly.atomicio.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.api.constants.AtomicIOConstant;
import io.github.vevoly.atomicio.api.constants.AtomicIOSessionAttributes;
import io.github.vevoly.atomicio.api.constants.IdleState;
import io.github.vevoly.atomicio.api.listeners.*;
import io.github.vevoly.atomicio.api.session.AtomicIOBindRequest;
import io.github.vevoly.atomicio.core.event.DisruptorManager;
import io.github.vevoly.atomicio.core.session.NettySession;
import io.github.vevoly.atomicio.api.AtomicIOMessage;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;
import reactor.util.annotation.Nullable;

import java.util.*;
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

    private final AtomicIOProperties config;
    private final AtomicIOCodecProvider codecProvider;
    private final AtomicIOClusterProvider clusterProvider;

    // Netty核心组件
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture serverChannelFuture;
    private final DisruptorManager disruptorManager = new DisruptorManager(); // Disruptor 管理器

    // 存储监听器集合
    private final List<EngineReadyListener> readyListeners = new CopyOnWriteArrayList<>();
    private final List<ConnectEventListener> connectEventListeners = new CopyOnWriteArrayList<>();
    private final List<DisconnectEventListener> disconnectEventListeners = new CopyOnWriteArrayList<>();
    private final List<MessageEventListener> messageListeners = new CopyOnWriteArrayList<>();
    private final List<ErrorEventListener> errorListeners = new CopyOnWriteArrayList<>();
    private final List<IdleEventListener> idleEventListeners = new CopyOnWriteArrayList<>();
    private final List<SessionReplacedListener> sessionReplacedListeners = new CopyOnWriteArrayList<>();

    // 映射表
    private final Map<String, AtomicIOSession> userIdToSessionMap = new ConcurrentHashMap<>(); // 单点登录时使用
    private final Map<String, CopyOnWriteArrayList<AtomicIOSession>> userIdToSessionsMap = new ConcurrentHashMap<>(); // 多点登录时使用
    private final Map<String, String> sessionIdToUserIdMap = new ConcurrentHashMap<>(); // Key: 会话ID, Value: 对应的用户ID
    private final Map<String, ChannelGroup> groups = new ConcurrentHashMap<>(); // Key: 组ID, Value: 组内的所有会话

    // 线程安全的状态机
    private final AtomicReference<AtomicIOLifeState> state = new AtomicReference<>(AtomicIOLifeState.NEW);

    public DefaultAtomicIOEngine(
            AtomicIOProperties config,
            @Nullable AtomicIOClusterProvider clusterProvider,
            AtomicIOCodecProvider codecProvider
    ) {
        this.config = Objects.requireNonNull(config, "EngineConfig cannot be null");
        this.clusterProvider = clusterProvider;
        this.codecProvider = Objects.requireNonNull(codecProvider, "CodecProvider cannot be null");
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
                        // 动态添加编解码器
                        codecProvider.buildPipeline(pipeline);
                        // 心跳检测
                        pipeline.addLast(AtomicIOConstant.PIPELINE_NAME_IDLE_STATE_HANDLER,
                                new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS));
                        // 核心处理器，事件翻译
                        pipeline.addLast(AtomicIOConstant.PIPELINE_NAME_CHANNEL_HANDLER, new EngineChannelHandler(disruptorManager, DefaultAtomicIOEngine.this));
                    }
                });
        serverChannelFuture = bootstrap.bind(config.getPort()).sync(); // 绑定端口
        log.info("Atomicio Engine bound successfully to port {}. codec: {}", config.getPort(), codecProvider.toString());
        // 在所有启动工作都完成后，宣告引擎就绪
        disruptorManager.publishEvent(AtomicIOEventType.READY, null, null, null);
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
    public void onReady(EngineReadyListener listener) {
        this.readyListeners.add(listener);
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
    public void onSessionReplaced(SessionReplacedListener listener) {
        this.sessionReplacedListeners.add(listener);
    }

    @Override
    public void bindUser(AtomicIOBindRequest request, AtomicIOSession newSession) {
        String userId = request.getUserId();
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(newSession, "Session cannot be null");

        // 处理任何可能存在的、与当前 session 关联的旧绑定
        String oldUserIdForThisSession = sessionIdToUserIdMap.put(newSession.getId(), userId);
        if (oldUserIdForThisSession != null && !oldUserIdForThisSession.equals(userId)) {
            // 这个 Session 之前绑定了另一个用户，现在要换绑
            // 这是一个罕见的场景，但需要正确处理
            log.warn("Session {} is being re-bound from old user '{}' to new user '{}'.",
                    newSession.getId(), oldUserIdForThisSession, userId);
            unbindUserInternal(oldUserIdForThisSession, newSession); // 用旧用户ID和新Session来清理
        }
        // 根据配置决定是多端登录还是单点登录
        if (config.getSession().isMultiLogin()) {
            // 多端登录逻辑
            CopyOnWriteArrayList<AtomicIOSession> sessions = userIdToSessionsMap.computeIfAbsent(
                    userId, k -> new CopyOnWriteArrayList<>()
            );
            sessions.add(newSession);
            log.info("User {} bound to a new session {} (Multi-session mode). Total sessions for user: {}.",
                    userId, newSession.getId(), sessions.size());
        } else {
            // 单点登录逻辑 (踢掉旧连接)
            AtomicIOSession oldSession = userIdToSessionMap.put(userId, newSession);
            if (oldSession != null && !oldSession.getId().equals(newSession.getId())) {
                log.warn("User {} re-logged in. replaced old session {}.", userId, oldSession.getId());
                fireSessionReplacedEvent(oldSession, newSession);
            }
        }
        // 将用户ID和设备ID等信息存储在 Session 属性中
        newSession.setAttribute(AtomicIOSessionAttributes.USER_ID, userId);
        if (request.getDeviceId() != null) {
            newSession.setAttribute(AtomicIOSessionAttributes.DEVICE_ID, request.getDeviceId());
        }
        newSession.setAttribute(AtomicIOSessionAttributes.IS_AUTHENTICATED, true);
        log.info("User {} successfully bound to session {}.", userId, newSession.getId());

    }

    @Override
    public void sendToUser(String userId, AtomicIOMessage message) {
        boolean sentLocally = sendToUserLocally(userId, message);
        if (!sentLocally && clusterProvider != null) {
            AtomicIOClusterMessage clusterMessage = buildClusterMessage(message, AtomicIOClusterMessageType.SEND_TO_USER, userId);
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

        if (config.getSession().isMultiLogin()) {
            // 多端模式
            List<AtomicIOSession> sessions = userIdToSessionsMap.get(userId);
            if (sessions != null && !sessions.isEmpty()) {
                log.debug("Sending message to user {} on {} device(s).", userId, sessions.size());
                sessions.forEach(s -> {
                    if (s.isActive()) s.send(message);
                });
                return true;
            }
        } else {
            // 单点模式
            AtomicIOSession session = userIdToSessionMap.get(userId);
            if (session != null && session.isActive()) {
                session.send(message);
                log.debug("Sent message to user {} (session {})", userId, session.getId());
                return true;
            }
        }
        return false;
    }

    @Override
    public void joinGroup(String groupId, String userId) {
        Objects.requireNonNull(groupId, "Group ID cannot be null");
        Objects.requireNonNull(userId, "User ID cannot be null");

        // 用户的所有 session 都加入群组
        List<AtomicIOSession> sessionsToJoin = findSessionsByUserId(userId);
        if (sessionsToJoin.isEmpty()) {
            log.warn("Cannot join group {}: User {} is not online.", groupId, userId);
            return;
        }
        log.info("User {} is joining group {} with all {} session(s).", userId, groupId, sessionsToJoin.size());
        for (AtomicIOSession session : sessionsToJoin) {
            joinGroup(groupId, session);
        }

    }

    @Override
    public void joinGroup(String groupId, AtomicIOSession session) {
        Objects.requireNonNull(groupId, "Group ID cannot be null");
        Objects.requireNonNull(session, "Session cannot be null");

        // 安全检查
        if (!session.isActive()) {
            log.warn("Cannot join group {}: Session {} is not active.", groupId, session.getId());
            return;
        }
        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        if (userId == null) {
            log.warn("Cannot join group {}: Session {} is not bound to a user.", groupId, session.getId());
            return;
        }
        // 获取或创建 ChannelGroup, GlobalEventExecutor.INSTANCE 确保 Group 资源的正确管理
        ChannelGroup group = groups.computeIfAbsent(groupId, k -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE));
        // 将 NettySession 内部的 Channel 加入到 ChannelGroup
        if (session instanceof NettySession) {
            group.add(((NettySession) session).getNettyChannel());
        } else {
            // 如果未来有其他 Session 实现，这里需要处理
            log.error("Unsupported session type for group operations: {}", session.getClass().getName());
            return;
        }
        // 将群组信息存储在 Session 属性中，方便后续清理
        Set<String> userGroups = session.getAttribute(AtomicIOSessionAttributes.GROUPS);
        if (userGroups == null) {
            userGroups = ConcurrentHashMap.newKeySet();
            session.setAttribute(AtomicIOSessionAttributes.GROUPS, userGroups);
        }
        // add 方法幂等，重复添加不会有问题
        boolean added = userGroups.add(groupId);
        if (added) {
            log.info("Session {} (user: {}) joined group {}. Current group size: {}",
                    session.getId(), userId, groupId, group.size());
        }
    }

    @Override
    public void leaveGroup(String groupId, String userId) {
        Objects.requireNonNull(groupId, "Group ID cannot be null");
        Objects.requireNonNull(userId, "User ID cannot be null");

        List<AtomicIOSession> sessionsToLeave = findSessionsByUserId(userId);
        if (sessionsToLeave.isEmpty()) {
            log.debug("User {} is not online, no need to leave group {} actively.", userId);
            return;
        }
        log.info("User {} is leaving group {} with all {} session(s).", userId, groupId, sessionsToLeave.size());
        for (AtomicIOSession session : sessionsToLeave) {
            leaveGroup(groupId, session);
        }
    }

    @Override
    public void leaveGroup(String groupId, AtomicIOSession session) {
        Objects.requireNonNull(groupId, "Group ID cannot be null");
        Objects.requireNonNull(session, "Session cannot be null");

        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        ChannelGroup group = groups.get(groupId);
        if (group != null) {
            // 从 ChannelGroup 中移除
            if (session instanceof NettySession) {
                group.remove(((NettySession) session).getNettyChannel());
            }
            // 从 Session 属性中移除
            Set<String> userGroups = session.getAttribute(AtomicIOSessionAttributes.GROUPS);
            if (userGroups != null) {
                boolean removed = userGroups.remove(groupId);
                if(removed){
                    log.info("Session {} (user: {}) left group {}. Current group size: {}",
                            session.getId(), userId, groupId, group.size());
                }
            }
            // 如果群组为空，则清理
            if (group.isEmpty()) {
                groups.remove(groupId);
                log.info("Group {} is empty and has been removed.", groupId);
            }
        }
    }

    @Override
    public void sendToGroup(String groupId, AtomicIOMessage message, String... excludeUserIds) {
        sendToGroupLocally(groupId, message, excludeUserIds);
        // 集群模式下，通知其他节点
        if (clusterProvider != null) {
            AtomicIOClusterMessage clusterMessage = buildClusterMessage(message, AtomicIOClusterMessageType.SEND_TO_GROUP, groupId, excludeUserIds);
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
                    String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
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
            AtomicIOClusterMessage clusterMessage = buildClusterMessage(message, AtomicIOClusterMessageType.BROADCAST, null);
            clusterProvider.publish(clusterMessage);
        }
    }

    @Override
    public List<AtomicIOSession> kickUser(String userId, @Nullable AtomicIOMessage kickOutMessage) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        log.warn("用户【{}】，被管理员强制下线.", userId);
        List<AtomicIOSession> kickedSessions = findSessionsByUserId(userId);
        if (kickedSessions.isEmpty()) {
            return Collections.emptyList();
        }
        for (AtomicIOSession session : kickedSessions) {
            if (session.isActive()) {
                if (kickOutMessage != null) {
                    // 1. 检查 session 是否是我们内部的 NettySession 实现
                    if (session instanceof NettySession) {
                        // 2. 如果是，便安全地进行转换，并调用 Netty 专属的方法
                        ChannelFuture sendFuture = ((NettySession) session).getNettyChannel().writeAndFlush(kickOutMessage);
                        // 3. 为 Netty 的 ChannelFuture 添加 CLOSE 监听器
                        sendFuture.addListener(ChannelFutureListener.CLOSE);
                        log.debug("Sent kick-out message to session {} and scheduled for closing.", session.getId());
                    } else {
                        // 安全回退: 如果 session 是其他未知实现，使用标准的 Future
                        log.warn("Session {} is not a NettySession. kick-out Failed.", session.getId());
                    }
                } else {
                    // 如果没有通知消息，直接关闭
                    session.close();
                }
            }
        }
        return kickedSessions;
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
     * 触发 READY 事件
     */
    void fireEngineReadyEvent() {
        for (EngineReadyListener listener : readyListeners) {
            try {
                listener.onEngineReady(this);
            } catch (Exception e) {
                log.error("Error executing EngineReadyListener", e);
            }
        }
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

    void fireSessionReplacedEvent(AtomicIOSession oldSession, AtomicIOSession newSession) {
        if (sessionReplacedListeners.isEmpty()) {
            log.warn("No SessionReplacedListeners registered. Closing old session {} by default.", oldSession.getId());
            if (oldSession.isActive()) {
                oldSession.close();
            }
            return;
        }
        for (SessionReplacedListener listener : sessionReplacedListeners) {
            try {
                listener.onSessionReplaced(oldSession, newSession);
            } catch (Exception e) {
                log.error("Error executing SessionReplacedListener", e);
            }
        }
        if (oldSession.isActive()) {
            log.debug("Closing old session {} after firing sessionReplaced event.", oldSession.getId());
            oldSession.close();
        }
    }


    /**
     * 引擎内部的清理方法：当 Session 断开时，自动解除用户绑定和群组关系。
     * 这个方法不应该暴露给外部，只由 EngineChannelHandler 调用。
     */
    void unbindUserInternal(String userId, AtomicIOSession session) {
        if (userId == null || session == null) {
            return;
        }
        // 从 SessionID -> UserID 的映射中移除
        sessionIdToUserIdMap.remove(session.getId());
        // 根据配置决定从哪个 Map 中移除
        if (config.getSession().isMultiLogin()) {
            // 多端登录清理逻辑
            CopyOnWriteArrayList<AtomicIOSession> sessions = userIdToSessionsMap.get(userId);
            if (sessions != null) {
                boolean removed = sessions.remove(session);
                if(removed) {
                    log.info("Session {} for user {} unbound due to disconnect. Remaining sessions: {}.",
                            session.getId(), userId, sessions.size());
                }
                if (sessions.isEmpty()) {
                    userIdToSessionsMap.remove(userId);
                    log.info("All sessions for user {} are disconnected. User is now offline.", userId);
                }
            }
        } else {
            // 单点登录清理逻辑
            // 仅当 Map 中存储的确实是当前这个 session 时才移除，防止并发问题
            boolean removed = userIdToSessionMap.remove(userId, session);
            if (removed) {
                log.info("User {} (session {}) unbound due to disconnect.", userId, session.getId());
            }
        }

        // 自动让用户离开所有他加入的群组
        Set<String> userGroups = session.getAttribute(AtomicIOSessionAttributes.GROUPS);
        if (userGroups != null && !userGroups.isEmpty()) {
            // 创建副本以避免在遍历时修改
            for (String groupId : new CopyOnWriteArraySet<>(userGroups)) {
                leaveGroup(groupId, session);
            }
            session.setAttribute(AtomicIOSessionAttributes.GROUPS, null);
        }
    }

    /**
     * 根据 userId 查找 session(s)
     * @param userId
     * @return
     */
    private List<AtomicIOSession> findSessionsByUserId(String userId) {
        if (config.getSession().isMultiLogin()) {
            return userIdToSessionsMap.getOrDefault(userId, new CopyOnWriteArrayList<>());
        } else {
            AtomicIOSession session = userIdToSessionMap.get(userId);
            return session != null ? List.of(session) : List.of();
        }
    }

    /**
     * 构建集群消息
     * @param message        消息
     * @param messageType    消息类型
     * @param target         目标
     * @param excludeUserIds 排除的用户
     * @return
     */
    private AtomicIOClusterMessage buildClusterMessage(AtomicIOMessage message, AtomicIOClusterMessageType messageType, String target, String... excludeUserIds) {
        AtomicIOClusterMessage clusterMessage = new AtomicIOClusterMessage();
        clusterMessage.setMessageType(messageType);
        clusterMessage.setCommandId(message.getCommandId());
        clusterMessage.setPayload(message.getPayload());
        if (null != target) {
            clusterMessage.setTarget(target);
        }
        if (excludeUserIds != null && excludeUserIds.length > 0) {
            clusterMessage.setExcludeUserIds(Set.of(excludeUserIds));
        }
        return clusterMessage;
    }

}
