package io.github.vevoly.atomicio.core.engine;

import io.github.vevoly.atomicio.api.AtomicIOEngine;
import io.github.vevoly.atomicio.api.AtomicIOEventType;
import io.github.vevoly.atomicio.api.AtomicIOMessage;
import io.github.vevoly.atomicio.api.AtomicIOSession;
import io.github.vevoly.atomicio.api.listeners.ErrorEventListener;
import io.github.vevoly.atomicio.api.listeners.MessageEventListener;
import io.github.vevoly.atomicio.api.listeners.SessionEventListener;
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
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

/**
 * AtomicIOEngine 的默认实现。
 * 负责启动和管理 Netty 服务器，以及所有会话的生命周期。
 *
 * @since 0.0.1
 * @author vevoly
 */
@Slf4j
public class DefaultAtomicIOEngine implements AtomicIOEngine {

    private final int port;

    // Netty核心组件
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture serverChannelFuture;

    // 存储监听器集合
    private final Map<AtomicIOEventType, List<SessionEventListener>> sessionListeners = new ConcurrentHashMap<>();
    private final List<MessageEventListener> messageListeners = new CopyOnWriteArrayList<>();
    private final List<ErrorEventListener> errorListeners = new CopyOnWriteArrayList<>();

    // 用户映射表
    private final Map<String, AtomicIOSession> userIdToSessionMap = new ConcurrentHashMap<>(); // Key: 用户ID, Value: 对应的会话
    private final Map<String, String> sessionIdToUserIdMap = new ConcurrentHashMap<>(); // Key: 会话ID, Value: 对应的用户ID

    // 群组映射表
    private final Map<String, ChannelGroup> groups = new ConcurrentHashMap<>(); // Key: 组ID, Value: 组内的所有会话

    public DefaultAtomicIOEngine(int port) {
        this.port = port;
    }

    @Override
    public Future<Void> start() {
        CompletableFuture<Void> startFuture = new CompletableFuture<>();
        // 初始化 Netty 线程组
        bossGroup = new NioEventLoopGroup(1);  // 负责接受连接
        workerGroup = new NioEventLoopGroup();          // 负责处理 I/O 操作

        try {
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
                            ChannelPipeline pipeline =socketChannel.pipeline();
                            // 添加编码器，因为它只处理出站消息
                            pipeline.addLast("encoder", new TextMessageEncoder());
                            // 帧解码器 (Frame Decoder): 解决 TCP 粘包/半包问题。
                            pipeline.addLast("frameDecoder", new LineBasedFrameDecoder(1024));
                            // 消息解码器 (Message Decoder):
                            pipeline.addLast("decoder", new TextMessageDecoder());
                            // 核心处理器，事件翻译
                            socketChannel.pipeline().addLast(new EngineChannelHandler(DefaultAtomicIOEngine.this));

                            log.info("New channel initialized: {}", socketChannel.remoteAddress());
                        }
                    });
            log.info("Atomicio Engine starting on port {}...", port);
            // 绑定端口
            serverChannelFuture = bootstrap.bind(port).sync();
            serverChannelFuture.addListener(future -> {
                if (future.isSuccess()) {
                    log.info("Atomicio Engine started successfully on port {}.", port);
                    startFuture.complete(null);
                } else {
                    log.error("Failed to start Atomicio Engine on port {}. Cause: {}", port, future.cause());
                    startFuture.completeExceptionally(future.cause());
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            startFuture.completeExceptionally(new RuntimeException("Engine start failed", e));
        }
        return startFuture;
    }

    @Override
    public void shutdown() {
        log.info("Atomicio Engine shutting down...");
        if (serverChannelFuture != null) {
            serverChannelFuture.channel().close().syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
        log.info("Atomicio Engine shutdown gracefully.");
    }

    @Override
    public void on(AtomicIOEventType eventType, SessionEventListener listener) {
        if (eventType == AtomicIOEventType.MESSAGE || eventType == AtomicIOEventType.ERROR) {
            throw new IllegalArgumentException("Please use onMessage() or onError() for this event type.");
        }
        this.sessionListeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
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
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(message, "Message cannot be null");

        AtomicIOSession session = userIdToSessionMap.get(userId);
        if (session != null && session.isActive()) {
            session.send(message);
            log.debug("Sent message {} to user {} (session {})", message.getCommandId(), userId, session.getId());
        } else {
            // 用户不在线或不在当前节点。
            // 在集群模式下，这里需要发布到 Redis Pub/Sub。
            log.warn("User {} is not online or not on this node. Message {} dropped locally.", userId, message.getCommandId());
            // TODO: 集群模式下，这里将消息发布到 Redis
        }
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
            // 如果用户不在线，就假设他已经离开了，或者不需要再次移除
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
        Objects.requireNonNull(groupId, "Group ID cannot be null");
        Objects.requireNonNull(message, "Message cannot be null");

        ChannelGroup group = groups.get(groupId);
        if (group != null && !group.isEmpty()) {
            if (excludeUserIds != null && excludeUserIds.length > 0) {
                // 如果有排除的用户，需要逐个发送，并跳过排除者
                Set<String> excludeSet = Set.of(excludeUserIds);
                group.forEach(channel -> {
                    AtomicIOSession session = new NettySession(channel); // 临时封装
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
        // TODO: 集群模式下，这里将消息发布到 Redis
    }

    @Override
    public void broadcast(AtomicIOMessage message) {
        Objects.requireNonNull(message, "Message cannot be null");

        // 遍历所有在线的 Session 并发送
        userIdToSessionMap.values().forEach(session -> {
            if (session.isActive()) {
                session.send(message);
            }
        });
        log.info("Broadcast message {} to all {} online sessions.", message.getCommandId(), userIdToSessionMap.size());
        // TODO: 集群模式下，这里将消息发布到 Redis
    }

    /**
     * 触发 Session 相关事件 (CONNECT, DISCONNECT, IDLE)
     * @param eventType 事件类型
     * @param session   当前会话
     */
    void fireSessionEvent(AtomicIOEventType eventType, AtomicIOSession session) {
        List<SessionEventListener> listeners = sessionListeners.get(eventType);
        if (null == listeners || listeners.isEmpty()) {
            return;
        }

        for (SessionEventListener listener : listeners) {
            try {
                listener.onEvent(session);
            } catch (Exception e) {
                log.error("Error executing listener for event {} on session {}", eventType, session.getId(), e);
                // 触发一个 ERROR 事件，让用户也能感知到监听器本身的异常
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
                listener.onEvent(session, message);
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
                listener.onEvent(session, cause);
            } catch (Exception e) {
                log.error("CRITICAL: Error executing the error listener itself!", e);
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
