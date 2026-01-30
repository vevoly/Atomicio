package io.github.vevoly.atomicio.core.engine;

import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.core.handler.LoginHandler;
import io.github.vevoly.atomicio.core.handler.RoutingHandler;
import io.github.vevoly.atomicio.protocol.api.constants.AtomicIOSessionAttributes;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessageType;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.server.api.codec.AtomicIOServerCodecProvider;
import io.github.vevoly.atomicio.server.api.constants.AtomicIOLifeState;
import io.github.vevoly.atomicio.server.api.constants.AtomicIOServerConstant;
import io.github.vevoly.atomicio.server.api.listeners.*;
import io.github.vevoly.atomicio.server.api.manager.*;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import io.github.vevoly.atomicio.server.api.state.AtomicIOStateProvider;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
    private final AtomicIOServerCodecProvider codecProvider; // 解码提供器
    @Getter
    private final AtomicIOClusterProvider clusterProvider; // 集群通信提供器
    @Getter
    private final AtomicIOStateProvider stateProvider; // 状态提供器

    // 管理器
    @Getter
    private final IOEventManager eventManager; // 事件管理器
    @Getter
    private final SessionManager sessionManager; // 会话管理器
    @Getter
    private final GroupManager groupManager; // 群组管理器
    @Getter
    private final DisruptorManager disruptorManager; // Disruptor 管理器
    @Getter
    private final ClusterManager clusterManager; // 集群管理器
    @Getter
    private final StateManager stateManager; // 状态管理器
    private final TransportManager nettyTransportManager; // 传输层管理器

    // 处理器
    private final LoginHandler loginHandler; // 登录处理器
    private final RoutingHandler routingHandler; // 路由处理器

    // 线程安全的状态机
    private final AtomicReference<AtomicIOLifeState> state = new AtomicReference<>(AtomicIOLifeState.NEW);

    public DefaultAtomicIOEngine(
            AtomicIOProperties config,
            TransportManager transportManager,
            DisruptorManager disruptorManager,
            IOEventManager eventManager,
            SessionManager sessionManager,
            GroupManager groupManager,
            AtomicIOServerCodecProvider codecProvider,
            AtomicIOStateProvider stateProvider,
            StateManager stateManager,
            AtomicIOClusterProvider clusterProvider,
            ClusterManager clusterManager
    ) {
        this.config = config;
        this.codecProvider = codecProvider;
        this.clusterProvider = clusterProvider;
        this.stateProvider = stateProvider;
        this.clusterManager = clusterManager;
        this.stateManager = stateManager;

        this.disruptorManager = disruptorManager;
        this.eventManager = eventManager;
        this.sessionManager = sessionManager;
        this.groupManager = groupManager;
        this.nettyTransportManager = transportManager;

        this.loginHandler = new LoginHandler(this);
        this.routingHandler = new RoutingHandler(this);
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
        }, AtomicIOServerConstant.ENGINE_THREAD_NAME).start();
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
            if (clusterManager != null) {
                log.info("启动集群管理器 ...");
                clusterManager.start();
            } else {
                log.info("单机模式，不启动集群管理器 ...");
            }
            // 3. 启动状态管理器
            if (stateManager != null) {
                log.info("启动状态管理器 ...");
                stateManager.start();
            }
            // 4. 启动 Netty 服务器
            nettyTransportManager.start().get(); // 阻塞等待 Netty 服务器启动完成
            // 5. 设置运行状态
            state.set(AtomicIOLifeState.RUNNING);
            // 6. 发布引擎就绪事件
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
        log.info("Atomicio 引擎 shutting down...");
        // 关闭 Netty 服务器
        nettyTransportManager.stop();
        // 关闭集群管理器
        if (this.clusterManager != null) {
            this.clusterManager.shutdown();
        }
        // 关闭状态管理器
        if (this.stateManager != null) {
            this.stateManager.shutdown();
        }
        // 关闭 Disruptor
        disruptorManager.shutdown();
        // 设置关闭状态
        state.set(AtomicIOLifeState.SHUTDOWN);
        log.info("Atomicio 引擎 shutdown gracefully.");
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
    public CompletableFuture<Void> bindUser(AtomicIOBindRequest request, AtomicIOSession newSession) {
        return loginHandler.login(request, newSession);
    }

    @Override
    public CompletableFuture<Void> unbindUser(AtomicIOSession session) {
        return loginHandler.logout(session);
    }

    @Override
    public void sendToUser(String userId, AtomicIOMessage message) {
        routingHandler.sendToUser(userId, message);
    }

    @Override
    public void sendToUsers(List<String> userIds, AtomicIOMessage message) {
        routingHandler.sendToUsers(userIds, message);
    }

    @Override
    public CompletableFuture<Void>  joinGroup(String groupId, String userId) {
        // 1. 调用 StateManager 进行全局状态更新（入库）
        return stateManager.joinGroup(groupId, userId).thenRun(() -> {
            // 2. 让该用户在本节点的所有物理 Session 加入物理 ChannelGroup
            List<AtomicIOSession> locals = sessionManager.getLocalSessionsByUserId(userId);
            locals.forEach(session -> groupManager.joinLocal(groupId, session));
        });
    }

    @Override
    public CompletableFuture<Void>  leaveGroup(String groupId, String userId) {
        // 调用逻辑层：全局注销状态
        return stateManager.leaveGroup(groupId, userId).thenRun(() -> {
            // 本地清理：让该用户的所有本地连接退出物理组
            List<AtomicIOSession> locals = sessionManager.getLocalSessionsByUserId(userId);
            locals.forEach(session -> groupManager.leaveLocal(groupId, session));
        });
    }

    @Override
    public void sendToGroup(String groupId, AtomicIOMessage message, Set<String> excludeUserIds) {
        routingHandler.sendToGroup(groupId, message, excludeUserIds);
    }

    @Override
    public void broadcast(AtomicIOMessage message) {
        routingHandler.broadcast(message);
    }

    @Override
    public List<AtomicIOSession> kickUser(String userId, @Nullable AtomicIOMessage kickOutMessage) {
        // 1. 获取本地快照（用于同步返回）
        List<AtomicIOSession> locals = sessionManager.getLocalSessionsByUserId(userId);
        // 2. 构建集群消息模板 (由 Engine 决定下线通知的内容)
        AtomicIOClusterMessage clusterMessage = clusterManager.buildClusterMessage(
                kickOutMessage, AtomicIOClusterMessageType.KICK_OUT, userId, null);
        if (clusterMessage == null) return locals;

        // 3. 调用 StateManager 清理状态，并利用返回的分布图进行投递
        stateManager.kickUserGlobalAndNotify(userId).thenAccept(kickedSessions -> {
            if (kickedSessions == null || kickedSessions.isEmpty()) return;

            // 4. 按节点分组投递
            Map<String, List<String>> nodeToDevices = kickedSessions.entrySet().stream()
                    .collect(Collectors.groupingBy(Map.Entry::getValue,
                            Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

            nodeToDevices.forEach((targetNodeId, deviceIds) -> {
                // 设置本次投递的具体设备名单
                clusterMessage.setTargetDeviceIds(deviceIds);

                if (clusterManager != null) {
                    // 集群模式：定向投递（含本地，确保进 Disruptor）
                    clusterManager.publishToNode(targetNodeId, clusterMessage);
                } else {
                    // 单机模式：直接推入本地 Disruptor
                    disruptorManager.publish(e -> e.setClusterMessage(clusterMessage));
                }
            });
        });
        return locals;
    }

    /**
     * 会话关闭时执行逻辑
     * @param session
     */
    @Override
    public void clearSession(AtomicIOSession session) {
        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        String deviceId = session.getAttribute(AtomicIOSessionAttributes.DEVICE_ID);

        // 1. 物理移除：从 SessionManager 的本地 Map 中移除（SessionManager 重构后的方法）
        sessionManager.removeLocalSession(session.getId());
        // 2. 物理层清理：从所有 ChannelGroup 移除引用 (GroupManager)
        groupManager.unbindGroupsForSession(session);
        // 3. 逻辑层清理：更新全局状态 (StateManager -> Provider)
        if (userId != null && deviceId != null) {
            stateManager.unregister(userId, deviceId)
                    .exceptionally(e -> {
                        log.error("注销用户 {} 的全局状态失败", userId, e);
                        return null;
                    });
        }
    }


}
