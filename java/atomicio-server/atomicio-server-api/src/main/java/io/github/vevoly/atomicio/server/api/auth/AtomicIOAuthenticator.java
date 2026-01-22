package io.github.vevoly.atomicio.server.api.auth;

import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.protocol.api.result.AuthResult;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;

import java.util.concurrent.CompletableFuture;

/**
 * 用户认证器接口
 * 框架使用者需要实现此接口，并将其注入到引擎中，以提供自定义的身份认证逻辑
 *
 * @since 0.6.5
 * @author vevoly
 */
public interface AtomicIOAuthenticator {

    /**
     * 异步执行身份认证。
     *
     * @param session 当前尝试登录的会话
     * @param message 客户端发送的登录消息 (LOGIN_REQUEST)
     * @return 一个 CompletableFuture，其结果是认证结果 AuthResult。
     *         如果认证成功，AuthResult 应包含 userId。
     *         如果认证失败，AuthResult 应包含失败原因。
     */
    CompletableFuture<AuthResult> authenticate(AtomicIOSession session, AtomicIOMessage message);
}
