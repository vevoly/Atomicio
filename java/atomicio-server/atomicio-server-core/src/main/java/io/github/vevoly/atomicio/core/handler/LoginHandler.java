package io.github.vevoly.atomicio.core.handler;

import io.github.vevoly.atomicio.common.api.config.KickDeviceStrategyType;
import io.github.vevoly.atomicio.common.api.config.LoginCollisionStrategyType;
import io.github.vevoly.atomicio.common.api.config.LoginStrategyType;
import io.github.vevoly.atomicio.common.api.dto.SessionDetails;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.constants.AtomicIOSessionAttributes;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * 登录封装
 * 这是一个无状态的策略执行器，由 DefaultAtomicIOEngine 调用。
 *
 * @since 0.6.10
 * @author vevoly
 */
@Slf4j
@RequiredArgsConstructor
public class LoginHandler {

    private final AtomicIOEngine engine;

    /**
     * 根据配置执行主登录流程。
     */
    public CompletableFuture<Void> login(AtomicIOBindRequest request, AtomicIOSession newSession) {
        final LoginStrategyType strategy = engine.getConfig().getSession().getLoginStrategy();
        return switch (strategy) {
            case MULTI_DEVICE -> handleMultiDeviceLogin(request, newSession);
            case LIMITED_MULTI_DEVICE -> handleLimitedMultiDeviceLogin(request, newSession);
            case SINGLE_PER_DEVICE_TYPE -> handleSinglePerDeviceTypeLogin(request, newSession);
            case SINGLE_ANY_DEVICE -> handleSingleAnyDeviceLogin(request, newSession);
        };
    }

    /**
     * 登出
     * @param session
     * @return
     */
    public CompletableFuture<Void> logout(AtomicIOSession session) {
        String userId = session.getAttribute(AtomicIOSessionAttributes.USER_ID);
        String deviceId = session.getAttribute(AtomicIOSessionAttributes.DEVICE_ID);

        if (userId == null) {
            log.warn("Session {} 尝试解绑，但未关联任何 UserId，忽略操作。", session.getId());
            return CompletableFuture.completedFuture(null);
        }
        log.info("正在为用户 {} (设备: {}) 执行逻辑解绑...", userId, deviceId);

        // 1. 通知 StateManager 移除全局在线状态
        return engine.getStateManager().unregister(userId, deviceId)
                .thenAccept(v -> {
                    // 2. 本地清理：从 SessionManager 的 UserId/DeviceId 索引中移除
                    engine.getSessionManager().removeLocalSessionOnly(session.getId());
                    // 3. 属性重置：恢复 Session 到未认证状态
                    session.removeAttribute(AtomicIOSessionAttributes.USER_ID);
                    session.removeAttribute(AtomicIOSessionAttributes.DEVICE_ID);
                    session.setAttribute(AtomicIOSessionAttributes.IS_AUTHENTICATED, false);
                    log.info("用户 {} 逻辑解绑成功，物理连接 {} 仍保持在线。", userId, session.getId());
                })
                .exceptionally(e -> {
                    log.error("用户 {} 解绑失败: {}", userId, e.getMessage());
//                     session.close();
                    throw new CompletionException(e);
                });
    }


    // --- 四种策略的具体实现 ---

    /**
     * 全局多点登录
     * @param request
     * @param newSession
     * @return
     */
    private CompletableFuture<Void> handleMultiDeviceLogin(AtomicIOBindRequest request, AtomicIOSession newSession) {
        return engine.getStateManager().register(request)
                .thenAccept(v -> engine.getSessionManager().bindLocalSession(
                        newSession.getId(),
                        request.getUserId(),
                        request.getDeviceId()
                ))
                .exceptionally(ex -> handleBindFailure(ex, newSession));
    }

    /**
     * 有限多点登录
     * @param request
     * @param newSession
     * @return
     */
    private CompletableFuture<Void> handleLimitedMultiDeviceLogin(AtomicIOBindRequest request, AtomicIOSession newSession) {
        return engine.getStateManager().findSessionDetails(request.getUserId())
                .thenCompose(currentSessions -> {
                    if (currentSessions.size() < engine.getConfig().getSession().getLimitedMultiDevice().getMaxInstances()) {
                        return handleMultiDeviceLogin(request, newSession);
                    }
                    Set<String> deviceToKick = calculateDevicesToEvict(currentSessions, 1);
                    return replaceSessionsAndFinalize(request, newSession, deviceToKick);
                });
    }

    /**
     * 按设备类型单点登录
     * @param request
     * @param newSession
     * @return
     */
    private CompletableFuture<Void> handleSinglePerDeviceTypeLogin(AtomicIOBindRequest request, AtomicIOSession newSession) {
        return engine.getStateManager().findSessionDetailsByType(request.getUserId(), request.getDeviceType())
                .thenCompose(oldSessionsOfType -> {
                    if (!oldSessionsOfType.isEmpty() && engine.getConfig().getSession().getLoginCollisionStrategy() == LoginCollisionStrategyType.REJECT_NEW) {
                        return rejectNewSession(newSession, "Another device of the same type is already online.");
                    }
                    return replaceSessionsAndFinalize(request, newSession, oldSessionsOfType.keySet());
                });
    }

    /**
     * 全局单点登录
     * @param request
     * @param newSession
     * @return
     */
    private CompletableFuture<Void> handleSingleAnyDeviceLogin(AtomicIOBindRequest request, AtomicIOSession newSession) {
        return engine.getStateManager().findSessionDetails(request.getUserId())
                .thenCompose(allOldSessions -> {
                    if (!allOldSessions.isEmpty() && engine.getConfig().getSession().getLoginCollisionStrategy() == LoginCollisionStrategyType.REJECT_NEW) {
                        return rejectNewSession(newSession, "Another device is already online.");
                    }
                    return replaceSessionsAndFinalize(request, newSession, allOldSessions.keySet());
                });
    }

    // --- 核心工作流的辅助方法 ---

    /**
     * 封装了“替换会话 -> 处理踢人 -> 入库新会话”的通用流程
     * @param request
     * @param newSession
     * @param devicesToKick
     * @return
     */
    private CompletableFuture<Void> replaceSessionsAndFinalize(AtomicIOBindRequest request, AtomicIOSession newSession, Set<String> devicesToKick) {
        return engine.getStateManager().replaceAndNotify(request, devicesToKick)
                .thenAccept(kickedMap -> {
                    // 处理本地替换事件
                    handleLocalSessionReplacement(newSession, kickedMap);
                    // 绑定 Session
                    engine.getSessionManager().bindLocalSession(
                            newSession.getId(),
                            request.getUserId(),
                            request.getDeviceId()
                    );
                    log.info("Session replacement successful for user '{}'.", request.getUserId());
                })
                .exceptionally(ex -> handleBindFailure(ex, newSession));
    }

    /**
     * 统一的绑定失败处理逻辑。
     * @param ex
     * @param newSession
     * @return
     */
    private Void handleBindFailure(Throwable ex, AtomicIOSession newSession) {
        log.error("Bind user workflow failed for session [{}].", newSession.getId(), ex.getCause());
        newSession.close();
        return null;
    }

    /**
     * 拒绝新会话的通用逻辑
     * @param newSession
     * @param reason
     * @return
     */
    private CompletableFuture<Void> rejectNewSession(AtomicIOSession newSession, String reason) {
        AtomicIOMessage rejectMessage = engine.getCodecProvider().createResponse(null, AtomicIOCommand.LOGIN_RESPONSE, false, reason);
        newSession.sendAndClose(rejectMessage);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 根据踢出策略，计算需要被踢掉的设备ID。
     * @param sessions
     * @param count
     * @return
     */
    private Set<String> calculateDevicesToEvict(Map<String, SessionDetails> sessions, int count) {
        if (sessions == null || sessions.isEmpty() || count <= 0) {
            return Collections.emptySet();
        }

        final KickDeviceStrategyType policy = engine.getConfig().getSession().getLimitedMultiDevice().getKickDeviceStrategy();
        //  区分驱逐策略
        return switch (policy) {
            case KICK_OLDEST_LOGIN -> sessions.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.comparingLong(SessionDetails::getLoginTime)))
                    .limit(count)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            case KICK_LEAST_RECENTLY_USED -> sessions.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.comparingLong(SessionDetails::getLastActivityTime)))
                    .limit(count)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            default -> {
                log.warn("Unknown eviction policy: {}. Falling back to KICK_OLDEST_LOGIN.", policy);
                // 安全降级：如果遇到未知的策略，默认使用最基础的 KICK_OLDEST_LOGIN
                yield sessions.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.comparingLong(SessionDetails::getLoginTime)))
                        .limit(count)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());
            }
        };
    }

    /**
     * 只处理发生在本节点的会话替换事件
     * @param newSession
     * @param kickedMap
     */
    private void handleLocalSessionReplacement(AtomicIOSession newSession, Map<String, String> kickedMap) {
        String currentNodeId = engine.getStateManager().getCurrentNodeId();

        kickedMap.forEach((deviceId, nodeId) -> {
            if (currentNodeId.equals(nodeId)) {
                AtomicIOSession oldSession = engine.getSessionManager().getLocalSessionByDeviceId(deviceId);
                if (oldSession != null) {
                    engine.getEventManager().fireSessionReplacedEvent(oldSession, newSession);
                }
            }
        });
    }

}
