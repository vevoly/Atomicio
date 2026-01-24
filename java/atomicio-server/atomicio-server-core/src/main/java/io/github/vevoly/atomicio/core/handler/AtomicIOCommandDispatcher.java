package io.github.vevoly.atomicio.core.handler;

import io.github.vevoly.atomicio.common.api.constants.AtomicIOConstant;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.codec.AtomicIOPayloadParser;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.auth.AtomicIOAuthenticator;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * 框架指令调度器
 * 负责处理框架指令，如心跳、踢人等
 *
 * @since 0.6.5
 * @author vevoly
 */
@Slf4j
@RequiredArgsConstructor
@ChannelHandler.Sharable
public class AtomicIOCommandDispatcher extends SimpleChannelInboundHandler<AtomicIOMessage> {

    private final AtomicIOEngine engine;
    private final AtomicIOPayloadParser payloadParser;
    private final AtomicIOAuthenticator authenticator;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AtomicIOMessage message) throws Exception {
        // 1. 获取 Session
        AtomicIOSession session = engine.getSessionManager().getLocalSessionById(ctx.channel().id().asLongText());
        if (session == null) {
            log.error("Session not found for active channel [{}].", ctx.channel().id());
            ctx.close();
            return;
        }

        final int cmd = message.getCommandId();

        // 优先处理框架命令
        if (handleFrameworkCommand(session, message)) {
            // 如果是框架命令且已被处理，则不再向下传递
            return;
        }

        // 对于非框架命令，检查会话是否已认证
        if (!session.isBound()) {
            // 安全策略：未登录的会话不能发送业务消息
            log.warn("Unauthorized message received from [{}], commandId={}. Closing session.", session.getRemoteAddress(), cmd);
            ctx.close();
            return;
        }

        // 将业务消息传递给下一个 Handler (DisruptorEventHandler)
        ctx.fireChannelRead(message);
    }

    /**
     * 根据指令ID分发并处理框架级命令。
     *
     * @param session 当前会话
     * @param message 消息对象
     * @return 如果消息是框架命令并已被处理，则返回 true；否则返回 false。
     */
    private boolean handleFrameworkCommand(AtomicIOSession session, AtomicIOMessage message) {
        final int commandId = message.getCommandId();

        // 对于需要认证的框架命令，先进行检查
        if (commandId == AtomicIOCommand.LOGOUT_REQUEST ||
                commandId == AtomicIOCommand.JOIN_GROUP_REQUEST ||
                commandId == AtomicIOCommand.LEAVE_GROUP_REQUEST) {
            if (!session.isBound()) {
                log.warn("AtomicIO 框架命令 (id={}) 需要绑定 session, 但是 [{}] 还未绑定.",
                        commandId, session.getRemoteAddress());
                session.close();
                return true;
            }
        }

        switch (commandId) {
            case AtomicIOCommand.LOGIN_REQUEST:
                handleLogin(session, message);
                return true;

            case AtomicIOCommand.LOGOUT_REQUEST:
                handleLogout(session);
                return true;

            case AtomicIOCommand.JOIN_GROUP_REQUEST:
                handleJoinGroup(session, message);
                return true;

            case AtomicIOCommand.LEAVE_GROUP_REQUEST:
                handleLeaveGroup(session, message);
                return true;

            case AtomicIOCommand.HEARTBEAT_REQUEST:
                handleHeartbeat(session, message);
                return true;

            default:
                // 不是框架命令无需关心
                return false;
        }
    }

    /**
     * 处理登录请求。这是一个异步过程。
     */
    private void handleLogin(AtomicIOSession session, AtomicIOMessage message) {
        authenticator.authenticate(session, message)
                .whenComplete((authResult, throwable) -> {
                    if (throwable != null) {
                        log.error("Authenticator threw an exception for session [{}].", session.getRemoteAddress(), throwable);
                        AtomicIOMessage response = engine.getCodecProvider()
                                .createResponse(message, AtomicIOCommand.LOGIN_RESPONSE, false, "Internal server error");
                        session.sendAndClose(response);
                        return;
                    }

                    if (authResult.success()) {
                        log.info("Authentication successful for user '{}', device '{}' on session [{}].",
                                authResult.userId(), authResult.deviceId(), session.getRemoteAddress());
                        AtomicIOBindRequest bindRequest = new AtomicIOBindRequest(authResult.userId()).withDeviceId(authResult.deviceId());
                        engine.bindUser(bindRequest, session);
                        AtomicIOMessage response = engine.getCodecProvider()
                                .createResponse(message, AtomicIOCommand.LOGIN_RESPONSE, true, "Welcome");
                        session.send(response);
                    } else {
                        log.warn("Authentication failed for session [{}]. Reason: {}", session.getRemoteAddress(), authResult.errorMessage());
                        AtomicIOMessage response = engine.getCodecProvider()
                                .createResponse(message, AtomicIOCommand.LOGIN_RESPONSE, false, "Error: " + authResult.errorMessage());
                        session.sendAndClose(response);
                    }
                });
    }

    /**
     * 处理登出请求。
     */
    private void handleLogout(AtomicIOSession session) {
        log.info("User '{}' on device '{}' requested logout. Closing session [{}].",
                session.getUserId(), session.getDeviceId(), session.getRemoteAddress());
        engine.kickUser(session.getUserId(), null);
    }

    /**
     * 处理加入群组请求。
     */
    private void handleJoinGroup(AtomicIOSession session, AtomicIOMessage message) {

        try {
            // 1. 使用 PayloadParser 进行协议无关的解析
            String groupId = payloadParser.parseAsString(message);
            if (groupId == null || groupId.isEmpty()) {
                log.warn("Join group request from user '{}' contains an empty groupId.", session.getUserId());
                // 回复一个失败的响应
                session.send(engine.getCodecProvider().createResponse(message, AtomicIOCommand.JOIN_GROUP_RESPONSE, false, "GroupId cannot be empty"));
                return;
            }
            log.debug("User '{}' joining group '{}'.", session.getUserId(), groupId);
            // todo 全局异步异常处理
            engine.joinGroup(groupId, session.getUserId())
                    .whenComplete((__, throwable) -> {
                        AtomicIOMessage response;
                        if (throwable != null) {
                            log.error("Failed to join group '{}' for user '{}'.", groupId, session.getUserId());
                            response = engine.getCodecProvider()
                                    .createResponse(message, AtomicIOCommand.JOIN_GROUP_RESPONSE, false, "Error: Failed to join group");
                        } else {
                            response = engine.getCodecProvider()
                                    .createResponse(message, AtomicIOCommand.JOIN_GROUP_RESPONSE, true, "Success: Joined group " + groupId);
                        }
                        session.send(response);
                    });
        } catch (Exception e) {
            log.error("Failed to parse JOIN_GROUP_REQUEST payload for user '{}'.", session.getUserId(), e);
            session.send(engine.getCodecProvider().createResponse(message, AtomicIOCommand.JOIN_GROUP_RESPONSE, false, "Malformed request payload"));
        }

    }

    /**
     * 处理离开群组请求。
     */
    private void handleLeaveGroup(AtomicIOSession session, AtomicIOMessage message) {
        try {
            String groupId = payloadParser.parseAsString(message);
            if (groupId.isEmpty()) {
                log.warn("Leave group request from user '{}' contains an empty groupId.", session.getUserId());
                return;
            }
            log.debug("User '{}' leaving group '{}'.", session.getUserId(), groupId);
            engine.leaveGroup(groupId, session.getUserId())
                    .whenComplete((__, throwable) -> {
                        AtomicIOMessage response;
                        if (throwable != null) {
                            log.error("Failed to leave group '{}' for user '{}'.", groupId, session.getUserId(), throwable);
                            response = engine.getCodecProvider()
                                    .createResponse(message, AtomicIOCommand.LEAVE_GROUP_RESPONSE, false, "Error: Failed to leave group");
                        } else {
                            response = engine.getCodecProvider()
                                    .createResponse(message, AtomicIOCommand.LEAVE_GROUP_RESPONSE, true, "Success: Left group " + groupId);
                        }
                        session.send(response);
                    });
        } catch (Exception e) {
            log.error("Failed to parse LEAVE_GROUP_REQUEST payload for user '{}'.", session.getUserId(), e);
            session.send(engine.getCodecProvider().createResponse(message, AtomicIOCommand.LEAVE_GROUP_RESPONSE, false, "Malformed request payload"));
        }
    }

    /**
     * 处理心跳请求，回复一个心跳响应。
     */
    private void handleHeartbeat(AtomicIOSession session, AtomicIOMessage message) {
        // 心跳的作用主要是保持连接活跃和检测死链，回复一个响应即可
        log.info("Received 💗 from session {}, responding.", session.getId());
        AtomicIOMessage heartbeatResponse = engine.getCodecProvider()
                .createResponse(message, AtomicIOCommand.HEARTBEAT_RESPONSE, true, AtomicIOConstant.DEFAULT_HEARTBEAT_RESPONSE);
        session.send(heartbeatResponse);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception caught in AtomicIOCommandDispatcher for channel [{}]: {}",
                ctx.channel().id(), cause.getMessage(), cause);
        ctx.close();
    }
}
