package io.github.vevoly.atomicio.core.engine;

import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
    private final ClusterManager clusterManager; // 集群管理器
    private final StateManager stateManager; // 状态管理器
    private final TransportManager nettyTransportManager; // 传输层管理器

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
    public CompletableFuture<Void>  bindUser(AtomicIOBindRequest request, AtomicIOSession newSession) {
        // 1. 设置 Session 基础属性（本地操作）
        sessionManager.bindLocalSession(newSession.getId(), request.getUserId(), request.getDeviceId());
        // 2. 向 StateManager 注册
        return stateManager.register(request, config.getSession().isMultiLogin())
                .thenAccept(kickedMap -> {
                    // 3. 执行踢人逻辑
                    if (kickedMap != null && !kickedMap.isEmpty()) {
                        handleSessionReplaced(request, kickedMap, newSession);
                    }
                });
    }

    @Override
    public CompletableFuture<Void> unbindUser(AtomicIOSession session) {
        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        String deviceId = session.getAttribute(AtomicIOSessionAttributes.DEVICE_ID);

        if (userId == null) {
            log.warn("Session {} 尝试解绑，但未关联任何 UserId，忽略操作。", session.getId());
            return CompletableFuture.completedFuture(null);
        }
        log.info("正在为用户 {} (设备: {}) 执行逻辑解绑...", userId, deviceId);

        // 1. 通知 StateManager 移除全局在线状态
        return stateManager.unregister(userId, deviceId)
                .thenAccept(v -> {
                    // 2. 本地清理：从 SessionManager 的 UserId/DeviceId 索引中移除
                    // 注意：这里不需要调用 sessionManager.removeLocalSession(id)，
                    // 因为那个方法会物理关闭连接，我们只需要清理索引。
                    // 建议在 SessionManager 中增加一个 clearIndexes(session) 方法，或者按如下逻辑：
                    sessionManager.removeLocalSessionOnly(session.getId());

                    // 3. 属性重置：恢复 Session 到未认证状态
                    session.removeAttribute(AtomicIOSessionAttributes.USER_ID);
                    session.removeAttribute(AtomicIOSessionAttributes.DEVICE_ID);
                    session.setAttribute(AtomicIOSessionAttributes.IS_AUTHENTICATED, false);
                    log.info("用户 {} 逻辑解绑成功，物理连接 {} 仍保持在线。", userId, session.getId());
                })
                .exceptionally(e -> {
                    log.error("用户 {} 解绑失败: {}", userId, e.getMessage());
                    // 视业务而定，如果解绑失败，是否强制关闭连接以保证状态一致性？
                    // session.close();
                    throw new CompletionException(e);
                });
    }

    @Override
    public void sendToUser(String userId, AtomicIOMessage message) {
        // 本地发送
        boolean sentLocally = sessionManager.sendToUserLocally(userId, message);
        if (sentLocally) return;
        // 不在本地则尝试进行远程投递
        if (clusterProvider != null) {
            stateManager.findNodeForUser(userId)
                    .thenAccept(targetNodeId -> {
                        if (targetNodeId != null && !targetNodeId.isEmpty()) {
                            // 找到目标节点
                            AtomicIOClusterMessage clusterMessage = clusterManager.buildClusterMessage(
                                    message, AtomicIOClusterMessageType.SEND_TO_USER, userId, null);
                            // 精准投递
                            clusterManager.publishToNode(targetNodeId, clusterMessage);
                            log.debug("远程投递 user {} 的包裹到节点 {}", userId, targetNodeId);
                        } else {
                            log.warn("未找到 user {} 的节点，丢弃消息.", userId);
                        }
                    })
                    .exceptionally(e -> {
                        log.error("查找 user {} 的节点失败，丢弃消息.", userId, e);
                        return null;
                    });
        }
    }

    @Override
    public void sendToUsers(List<String> userIds, AtomicIOMessage message) {
        if (userIds == null || userIds.isEmpty()) return;

        // 1. 先处理所有在本地的 session，直接发送
        List<String> remoteUserIds = new ArrayList<>();
        for (String userId : userIds) {
            if (!sessionManager.sendToUserLocally(userId, message)) {
                // 如果本地发送失败，说明用户不在当前节点
                remoteUserIds.add(userId);
            }
        }
        if (remoteUserIds.isEmpty() || clusterProvider == null) {
            return; // 所有用户都在本地，或没有集群，任务结束
        }

        // 2. 从 StateProvider 查询所有远程用户的节点位置
        stateManager.findNodesForUsers(remoteUserIds) // 返回 Future<Map<String, String>> userId -> nodeId
            .whenComplete((userNodeMap, throwable) -> {
                if (throwable != null) {
                    log.error("批量查询用户节点失败.", throwable);
                    return;
                }
                // 3. 将用户按目标节点进行分组
                Map<String, List<String>> nodeUserMap = new HashMap<>();
                userNodeMap.forEach((userId, nodeId) -> {
                    nodeUserMap.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(userId);
                });
                // 4. 为每个目标节点，构建并发送一条批量集群消息
                nodeUserMap.forEach((nodeId, usersOnNode) -> {
                    AtomicIOClusterMessage clusterMessage = clusterManager.buildClusterMessage(
                            message, AtomicIOClusterMessageType.SEND_TO_USERS_BATCH, usersOnNode, null);

                    // 通过 ClusterManager 发送到指定节点
                    clusterManager.publishToNode(nodeId, clusterMessage);
                });
            });
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
    public CompletableFuture<Void>  joinGroup(String groupId, AtomicIOSession session) {
        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        if (userId == null) {
            return null;
        }

        // 1. 调用逻辑层：更新全局状态
        return stateManager.joinGroup(groupId, userId).thenRun(() -> {
            // 2. 调用物理层：将连接加入本地 ChannelGroup
            groupManager.joinLocal(groupId, session);
            log.debug("Session {} joined group {} locally & globally", session.getId(), groupId);
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
    public CompletableFuture<Void>  leaveGroup(String groupId, AtomicIOSession session) {
        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        return stateManager.leaveGroup(groupId, userId).thenRun(() -> {
            groupManager.leaveLocal(groupId, session);
        });
    }

    @Override
    public void sendToGroup(String groupId, AtomicIOMessage message, Set<String> excludeUserIds) {
        // 先进行一次预编码，后续分发都使用这个字节流
        AtomicIOClusterMessage clusterMessage = clusterManager.buildClusterMessage(message, AtomicIOClusterMessageType.SEND_TO_GROUP, groupId, excludeUserIds);
        // 本地广播：利用物理 ChannelGroup 高效推送
        groupManager.sendToGroupLocally(groupId, message, excludeUserIds);
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
            AtomicIOClusterMessage clusterMessage = clusterManager.buildClusterMessage(message, AtomicIOClusterMessageType.BROADCAST, null, null);
            clusterManager.publish(clusterMessage);
        }
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
        stateManager.kickUserGlobal(userId).thenAccept(kickedSessions -> {
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

    /**
     * 构建挤下线通知消息
     */
    /**
     * 创建一个（批量）踢人指令的集群消息。
     */
    private AtomicIOClusterMessage buildKickOutMessage(String userId, List<String> deviceIds) {
        AtomicIOClusterMessage msg = new AtomicIOClusterMessage();
        msg.setMessageType(AtomicIOClusterMessageType.KICK_OUT);
        msg.setTargetUserId(userId);
        msg.setTargetDeviceIds(deviceIds);
        msg.setFromNodeId(clusterManager.getCurrentNodeId());
        return msg;
    }

    /**
     * 处理账号挤下线冲突
     * 逻辑：按节点分组，并发送带通知的 KICK_OUT 指令
     */
    private void handleSessionReplaced(AtomicIOBindRequest request, Map<String, String> kickedMap, AtomicIOSession newSession) {
        // 1. 构建下线通知 (Payload)
        AtomicIOMessage kickNotify = this.getCodecProvider()
                .createResponse(null, AtomicIOCommand.KICK_OUT_NOTIFY, true, "Kick out by server");
        // 2. 按节点分组 (deviceId -> nodeId => nodeId -> List<deviceId>)
        Map<String, List<String>> nodeToDevices = kickedMap.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ));

        // 3. 遍历分组进行定向投递
        nodeToDevices.forEach((targetNodeId, deviceIds) -> {
            if (targetNodeId.equals(clusterManager.getCurrentNodeId())) {
                // 如果是当前节点，直接调用本地方法
                for (String deviceId : deviceIds) {
                    AtomicIOSession oldSession = sessionManager.getLocalSessionByDeviceId(deviceId);
                    if (oldSession != null) {
                        eventManager.fireSessionReplacedEvent(oldSession, newSession);
                    }
                }
            } else {
                // 如果是其他节点，调用远程方法
                AtomicIOClusterMessage clusterMessage = clusterManager.buildClusterMessage(
                        kickNotify,
                        AtomicIOClusterMessageType.KICK_OUT,
                        request.getUserId(),
                        null
                );
                // 集群模式: 精准投递
                clusterManager.publishToNode(targetNodeId, clusterMessage);
                log.debug("Collision: Sent KICK_OUT to remote node {} for user {}", targetNodeId, request.getUserId());
            }
        });
    }

}
