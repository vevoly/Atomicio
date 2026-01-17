package io.github.vevoly.atomicio.core.engine;

import io.github.vevoly.atomicio.common.api.config.AtomicIOConfigDefaultValue;
import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.core.manager.*;
import io.github.vevoly.atomicio.core.session.NettySession;
import io.github.vevoly.atomicio.protocol.api.AtomicIOMessage;
import io.github.vevoly.atomicio.protocol.api.constants.AtomicIOSessionAttributes;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessageType;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.server.api.codec.AtomicIOServerCodecProvider;
import io.github.vevoly.atomicio.server.api.constants.AtomicIOLifeState;
import io.github.vevoly.atomicio.server.api.listeners.*;
import io.github.vevoly.atomicio.server.api.manager.ClusterManager;
import io.github.vevoly.atomicio.server.api.manager.DisruptorManager;
import io.github.vevoly.atomicio.server.api.manager.StateManager;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import io.github.vevoly.atomicio.server.api.state.AtomicIOStateProvider;
import io.netty.channel.ChannelFutureListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private final AtomicIOServerCodecProvider codecProvider; // 解码提供器
    @Getter
    private final AtomicIOClusterProvider clusterProvider; // 集群通信提供器
    @Getter
    private final AtomicIOStateProvider stateProvider; // 状态提供器

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
    private final ClusterManager clusterManager; // 集群管理器
    private final StateManager stateManager; // 状态管理器

    // 线程安全的状态机
    private final AtomicReference<AtomicIOLifeState> state = new AtomicReference<>(AtomicIOLifeState.NEW);

    public DefaultAtomicIOEngine(
            AtomicIOProperties config,
            DisruptorManager disruptorManager,
            AtomicIOEventManager eventManager,
            AtomicIOSessionManager sessionManager,
            AtomicIOGroupManager groupManager,
            AtomicIOServerCodecProvider codecProvider,
            AtomicIOStateProvider stateProvider,
            StateManager stateManager,
            @Nullable AtomicIOClusterProvider clusterProvider,
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
        this.nettyServerManager = new NettyServerManager(this);

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
            nettyServerManager.start().get(); // 阻塞等待 Netty 服务器启动完成
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
        nettyServerManager.stop();
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
    public void bindUser(AtomicIOBindRequest request, AtomicIOSession newSession) {
        String userId = request.getUserId();
        String currentNodeId = (clusterProvider != null) ? clusterProvider.getCurrentNodeId() : AtomicIOConfigDefaultValue.SYS_ID;

        // 1. 设置 Session 基础属性（本地操作）
        newSession.setAttribute(AtomicIOSessionAttributes.USER_ID, userId);
        if (request.getDeviceId() != null) {
            newSession.setAttribute(AtomicIOSessionAttributes.DEVICE_ID, request.getDeviceId());
        }
        newSession.setAttribute(AtomicIOSessionAttributes.IS_AUTHENTICATED, true);

        // 2. 核心决策：交给 StateManager 进行全局注册
        stateManager.register(request, config.getSession().isMultiLogin())
                .thenAccept(kickMap -> {
                    // 3. 处理本地冲突：StateManager 故意留给 Engine 处理本地 Session 冲突
                    if (kickMap != null && !kickMap.isEmpty()) {
                        kickMap.forEach((deviceId, nodeId) -> {
                            // 只有当被踢设备在本机时，才调用 sessionManager 执行物理断开
                            if (currentNodeId.equals(nodeId)) {
                                sessionManager.removeByDeviceId(deviceId);
                            }
                        });
                    }
                    // 4. 物理入库：将连接交给 SessionManager 管理
                    sessionManager.addLocalSession(newSession);
                    log.info("User {} bound successfully on node {}", userId, currentNodeId);
                })
                .exceptionally(e -> {
                    log.error("Bind failed for user {}", userId, e);
                    newSession.close();
                    return null;
                });
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
        // 1. 调用 StateManager 进行全局状态更新（入库）
        stateManager.joinGroup(groupId, userId).thenRun(() -> {
            // 2. 让该用户在本节点的所有物理 Session 加入物理 ChannelGroup
            List<AtomicIOSession> locals = sessionManager.findLocalSessionsByUserId(userId);
            locals.forEach(session -> groupManager.joinLocal(groupId, session));
        });
    }

    @Override
    public void joinGroup(String groupId, AtomicIOSession session) {
        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        if (userId == null) {
            return;
        }

        // 1. 调用逻辑层：更新全局状态
        stateManager.joinGroup(groupId, userId).thenRun(() -> {
            // 2. 调用物理层：将连接加入本地 ChannelGroup
            groupManager.joinLocal(groupId, session);
            log.debug("Session {} joined group {} locally & globally", session.getId(), groupId);
        });
    }

    @Override
    public void leaveGroup(String groupId, String userId) {
        // 调用逻辑层：全局注销状态
        stateManager.leaveGroup(groupId, userId).thenRun(() -> {
            // 本地清理：让该用户的所有本地连接退出物理组
            List<AtomicIOSession> locals = sessionManager.findLocalSessionsByUserId(userId);
            locals.forEach(session -> groupManager.leaveLocal(groupId, session));
        });
    }

    @Override
    public void leaveGroup(String groupId, AtomicIOSession session) {
        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        stateManager.leaveGroup(groupId, userId).thenRun(() -> {
            groupManager.leaveLocal(groupId, session);
        });
    }

    @Override
    public void sendToGroup(String groupId, AtomicIOMessage message, String... excludeUserIds) {
        // 先进行一次预编码，后续分发都使用这个字节流
        AtomicIOClusterMessage clusterMessage = buildClusterMessage(message, AtomicIOClusterMessageType.SEND_TO_GROUP, groupId, excludeUserIds);
        // 本地广播：利用物理 ChannelGroup 高效推送
        groupManager.broadcastLocally(groupId, message);
        // 集群分发：通知其他节点
        if (clusterProvider != null) {
            clusterManager.publish(clusterMessage);
        }
    }

    @Override
    public void broadcast(AtomicIOMessage message) {
        // 本地全量推送
        sessionManager.broadcastLocally(message);
        // 集群广播
        if (clusterProvider != null) {
            AtomicIOClusterMessage clusterMessage = buildClusterMessage(message, AtomicIOClusterMessageType.BROADCAST, null);
            clusterManager.publish(clusterMessage);
        }
    }

    @Override
    public List<AtomicIOSession> kickUser(String userId, @Nullable AtomicIOMessage kickOutMessage) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        log.warn("管理员发起全局强制下线: userId={}", userId);

        // 1. 获取本地要踢的连接副本
        List<AtomicIOSession> locals = sessionManager.findLocalSessionsByUserId(userId);
        // 2. 执行全局注销，StateManager 内部会自动通过 ClusterManager 通知远端节点
        stateManager.kickUserGlobal(userId).thenRun(() -> {
            // 3. 本地物理执行
            for (AtomicIOSession session : locals) {
                if (!session.isActive()) {
                    continue;
                }
                if (kickOutMessage != null && session instanceof NettySession) {
                    // Netty：发送后直接关闭 Channel
                    ((NettySession) session).getNettyChannel()
                            .writeAndFlush(kickOutMessage)
                            .addListener(ChannelFutureListener.CLOSE);
                } else {
                    session.close();
                }
            }
        });

        return locals;
    }

    /**
     * 会话关闭时执行逻辑
     * @param session
     */
    public void onSessionClosed(AtomicIOSession session) {
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

    /**
     * 构建集群消息
     * @param message        消息
     * @param messageType    消息类型
     * @param target         目标
     * @param excludeUserIds 排除的用户
     * @return
     */
    private AtomicIOClusterMessage buildClusterMessage (
            AtomicIOMessage message,
            AtomicIOClusterMessageType messageType,
            String target,
            String... excludeUserIds
    ) {
        // 1. 预编码
        byte[] finalPayload = new byte[0];
        try {
            finalPayload = codecProvider.encodeToBytes(message, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        AtomicIOClusterMessage clusterMessage = new AtomicIOClusterMessage();
        clusterMessage.setMessageType(messageType);
        clusterMessage.setCommandId(message.getCommandId());
        // 2. payload 存储的是“预编码”后的最终字节
        clusterMessage.setPayload(finalPayload);
        if (target != null) {
            clusterMessage.setTarget(target);
        }
        if (excludeUserIds != null && excludeUserIds.length > 0) {
            clusterMessage.setExcludeUserIds(Set.of(excludeUserIds));
        }
        return clusterMessage;
    }

}
