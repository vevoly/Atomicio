package io.github.vevoly.atomicio.core.engine;

import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.constants.AtomicIOLifeState;
import io.github.vevoly.atomicio.common.api.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.AtomicIOSession;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessageType;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.server.api.AtomicIOCodecProvider;
import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.server.api.listeners.*;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;
import io.github.vevoly.atomicio.core.manager.*;
import io.github.vevoly.atomicio.core.session.NettySession;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.util.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
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

    @Getter
    private final AtomicIOProperties config; // 配置文件
    @Getter
    private final AtomicIOCodecProvider codecProvider; // 解码提供器
    @Getter
    private final AtomicIOClusterProvider clusterProvider; // 集群通信提供器

    // 管理器
    @Getter
    private final AtomicIOEventManager eventManager; // 事件管理器
    @Getter
    private final AtomicIOSessionManager sessionManager; // 会话管理器
    @Getter
    private final AtomicIOGroupManager groupManager; // 群组管理器
    @Getter
    private final DisruptorManager disruptorManager; // Disruptor 管理器
    private final NettyServerManager nettyServerManager; // Netty 管理器
    private final AtomicIOClusterManager clusterManager; // 集群管理器

    // 线程安全的状态机
    private final AtomicReference<AtomicIOLifeState> state = new AtomicReference<>(AtomicIOLifeState.NEW);

    public DefaultAtomicIOEngine(
            AtomicIOProperties config,
            @Nullable AtomicIOClusterProvider clusterProvider,
            AtomicIOCodecProvider codecProvider
    ) {
        this.config = config;
        this.codecProvider = codecProvider;
        this.clusterProvider = clusterProvider;

        this.disruptorManager = new DisruptorManager();
        this.eventManager = new AtomicIOEventManager();
        this.sessionManager = new AtomicIOSessionManager(this);
        this.groupManager = new AtomicIOGroupManager(this);
        this.nettyServerManager = new NettyServerManager(this);
        this.clusterManager = new AtomicIOClusterManager(this.clusterProvider, this.disruptorManager);
    }

    // -- 生命周期管理 --

    @Override
    public Future<Void> start() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        new Thread(() -> {
            try {
                log.info("Atomicio Engine is starting (async)...");
                doStart(); // 调用同步阻塞的 doStart
                future.complete(null);
            } catch (Exception e) {
                log.error("Failed to start AtomicIO Engine.", e);
                future.completeExceptionally(e);
            }
        }, "atomicio-start-thread").start();
        return future;
    }

    @Override
    public void shutdown() {
        doStop();
    }

    /**
     * 核心启动逻辑，供 start() 和 LifecycleManager 调用。
     */
    public void doStart() {
        if (!state.compareAndSet(AtomicIOLifeState.NEW, AtomicIOLifeState.STARTING)) {
            if (state.get() == AtomicIOLifeState.RUNNING || state.get() == AtomicIOLifeState.STARTING) {
                return;
            }
            throw new IllegalStateException("Atomicio Engine is not in a startable state.");
        }

        try {
            log.info("Starting internal components...");

            // 1. 启动 disruptor
            disruptorManager.start(this);
            // 2. 启动集群服务
            clusterManager.start();
            // 3. 启动 Netty 服务器
            nettyServerManager.start().get(); // 阻塞等待 Netty 服务器启动完成
            // 4. 设置运行状态
            state.set(AtomicIOLifeState.RUNNING);
            // 5. 发布引擎就绪事件
            eventManager.fireEngineReadyEvent(this);
        } catch (Exception e) {
            log.error("Atomicio Engine startup failed. Initiating shutdown...", e);
            doStop(); // 清理资源
            throw new RuntimeException("Engine start failed", e);
        }
    }

    /**
     * 核心关闭逻辑，供 shutdown() 和 LifecycleManager 调用。
     */
    public void doStop() {
        if (!state.compareAndSet(AtomicIOLifeState.RUNNING, AtomicIOLifeState.SHUTTING_DOWN)) {
            // 如果引擎从未运行过，或者正在关闭/已关闭，则直接返回
            log.warn("Atomicio Engine is not running or already shutting down. Current state: {}", state.get());
            return;
        }
        log.info("Atomicio Engine shutting down...");
        // 关闭 Netty 服务器
        nettyServerManager.stop();

        // 关闭 ClusterProvider
        if (this.clusterProvider != null) {
            clusterProvider.shutdown();
        }
        // 关闭 Disruptor
        disruptorManager.shutdown();
        // 设置关闭状态
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
    public void onConnectionReject(ConnectionRejectListener listener) {
        eventManager.onConnectionReject(listener);
    }
    @Override
    public void onReady(EngineReadyListener listener) {
        eventManager.onReady(listener);
    }

    @Override
    public void onConnect(ConnectEventListener listener) {
        eventManager.onConnect(listener);
    }
    @Override
    public void onDisconnect(DisconnectEventListener listener) {
        eventManager.onDisconnect(listener);
    }
    @Override
    public void onMessage(MessageEventListener listener) {
        eventManager.onMessage(listener);
    }
    @Override
    public void onError(ErrorEventListener listener) {
        eventManager.onError(listener);
    }
    @Override
    public void onIdle(IdleEventListener listener) {
        eventManager.onIdle(listener);
    }
    @Override
    public void onSessionReplaced(SessionReplacedListener listener) {
        eventManager.onSessionReplaced(listener);
    }

    @Override
    public void bindUser(AtomicIOBindRequest request, AtomicIOSession newSession) {
        sessionManager.bindUser(request, newSession);
    }

    @Override
    public void sendToUser(String userId, AtomicIOMessage message) {
        boolean sentLocally = sessionManager.sendToUserLocally(userId, message);
        if (!sentLocally && clusterProvider != null) {
            AtomicIOClusterMessage clusterMessage = buildClusterMessage(message, AtomicIOClusterMessageType.SEND_TO_USER, userId);
            clusterManager.publish(clusterMessage);
        }
    }

    @Override
    public void joinGroup(String groupId, String userId) {
        // 用户的所有 session 都加入群组
        List<AtomicIOSession> sessionsToJoin = sessionManager.findSessionsByUserId(userId);
        if (sessionsToJoin.isEmpty()) {
            log.warn("Cannot join group {}: User {} is not online.", groupId, userId);
            return;
        }
        sessionsToJoin.forEach(session -> groupManager.joinGroup(groupId, session));
    }

    @Override
    public void joinGroup(String groupId, AtomicIOSession session) {
        groupManager.joinGroup(groupId, session);
    }

    @Override
    public void leaveGroup(String groupId, String userId) {
        List<AtomicIOSession> sessionsToLeave = sessionManager.findSessionsByUserId(userId);
        if (sessionsToLeave.isEmpty()) {
            log.debug("User {} is not online, no need to leave group {} actively.", userId);
            return;
        }
        log.info("User {} is leaving group {} with all {} session(s).", userId, groupId, sessionsToLeave.size());
        sessionsToLeave.forEach(session -> groupManager.leaveGroup(groupId, session));
    }

    @Override
    public void leaveGroup(String groupId, AtomicIOSession session) {
        groupManager.leaveGroup(groupId, session);
    }

    @Override
    public void sendToGroup(String groupId, AtomicIOMessage message, String... excludeUserIds) {
        groupManager.sendToGroupLocally(groupId, message, excludeUserIds);
        // 集群模式下，通知其他节点
        if (clusterProvider != null) {
            AtomicIOClusterMessage clusterMessage = buildClusterMessage(message, AtomicIOClusterMessageType.SEND_TO_GROUP, groupId, excludeUserIds);
            clusterManager.publish(clusterMessage);
        }
    }

    @Override
    public void broadcast(AtomicIOMessage message) {
        sessionManager.broadcastLocally(message);
        if (clusterProvider != null) {
            AtomicIOClusterMessage clusterMessage = buildClusterMessage(message, AtomicIOClusterMessageType.BROADCAST, null);
            clusterManager.publish(clusterMessage);
        }
    }

    @Override
    public List<AtomicIOSession> kickUser(String userId, @Nullable AtomicIOMessage kickOutMessage) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        log.warn("用户【{}】，被管理员强制下线.", userId);
        List<AtomicIOSession> kickedSessions = sessionManager.findSessionsByUserId(userId);
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
     * 构建集群消息
     * @param message        消息
     * @param messageType    消息类型
     * @param target         目标
     * @param excludeUserIds 排除的用户
     * @return
     */
    private AtomicIOClusterMessage buildClusterMessage(
            AtomicIOMessage message,
            AtomicIOClusterMessageType messageType,
            String target,
            String... excludeUserIds
    ) {
        AtomicIOClusterMessage clusterMessage = new AtomicIOClusterMessage();
        clusterMessage.setMessageType(messageType);
        clusterMessage.setCommandId(message.getCommandId());
        byte[] payloadBytes = message.getPayload();
        if (payloadBytes != null && payloadBytes.length > 0) {
            String base64Payload = Base64.getEncoder().encodeToString(payloadBytes);
            clusterMessage.setPayload(base64Payload.getBytes(StandardCharsets.UTF_8));
        } else {
            clusterMessage.setPayload(new byte[0]);
        }
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
