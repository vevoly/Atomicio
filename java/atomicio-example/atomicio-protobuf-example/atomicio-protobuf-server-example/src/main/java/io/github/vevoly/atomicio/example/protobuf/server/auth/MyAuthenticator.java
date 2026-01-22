package io.github.vevoly.atomicio.example.protobuf.server.auth;

import io.github.vevoly.atomicio.codec.protobuf.proto.LoginRequest;
import io.github.vevoly.atomicio.protocol.api.codec.AtomicIOPayloadParser;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.protocol.api.result.AuthResult;
import io.github.vevoly.atomicio.server.api.auth.AtomicIOAuthenticator;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 业务服务器端实现的认证器
 * 框架将在收到 LOGIN_REQUEST 指令时，自动调用此类的 authenticate 方法
 *
 * @since 0.6.7
 * @author vevoly
 */
@Slf4j
@Component
@AllArgsConstructor
public class MyAuthenticator implements AtomicIOAuthenticator {

    private final AtomicIOPayloadParser protobufPayloadParser;

    @Override
    public CompletableFuture<AuthResult> authenticate(AtomicIOSession session, AtomicIOMessage message) {
        try {
            // 1. 解包成框架定义的 LoginRequest
            LoginRequest loginRequest = protobufPayloadParser.parse(message, LoginRequest.class);
            String userId = loginRequest.getUserId();
            String token = loginRequest.getToken();
            String deviceId = loginRequest.getDeviceId();

            log.info("Handling authentication for user: {}, device: {}", userId, deviceId);

            // 2. 模拟业务认证逻辑
            if (isValidToken(token)) {
                // 3. 认证成功，返回包含 userId 和 deviceId 的成功结果
                return CompletableFuture.completedFuture(AuthResult.success(userId, deviceId));
            } else {
                // 4. 认证失败，返回包含错误信息的失败结果
                return CompletableFuture.completedFuture(AuthResult.failure("Invalid token"));
            }
        } catch (Exception e) {
            log.error("Failed to parse login request payload.", e);
            return CompletableFuture.completedFuture(AuthResult.failure("Malformed login request."));
        }
    }

    // 模拟的认证服务
    private boolean isValidToken(String token) {
        // TODO: 在真实项目中实现真正的 token 验证逻辑
        return token != null && !token.isEmpty();
    }
}
