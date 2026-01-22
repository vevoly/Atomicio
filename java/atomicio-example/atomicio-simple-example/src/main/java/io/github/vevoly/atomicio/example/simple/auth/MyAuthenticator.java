package io.github.vevoly.atomicio.example.simple.auth;

import io.github.vevoly.atomicio.codec.text.TextMessage;
import io.github.vevoly.atomicio.example.simple.service.AuthService;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.protocol.api.result.AuthResult;
import io.github.vevoly.atomicio.server.api.auth.AtomicIOAuthenticator;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 用户自定义认证器实现
 * 框架将在收到 LOGIN_REQUEST 指令时，自动调用此类的 authenticate 方法。
 * 注意：框架要求使用者必须提供自定义认证器！！！
 * 注意：框架要求使用者必须提供自定义认证器！！！
 * 注意：框架要求使用者必须提供自定义认证器！！！
 *
 * @since 0.6.5
 * @author vevoly
 */
@Slf4j
@Component
public class MyAuthenticator implements AtomicIOAuthenticator {


    @Override
    public CompletableFuture<AuthResult> authenticate(AtomicIOSession session, AtomicIOMessage message) {
        if (!(message instanceof TextMessage)) {
            return CompletableFuture.completedFuture(AuthResult.failure("Unsupported message type for login."));
        }

        TextMessage textMessage = (TextMessage) message;
        String content = textMessage.getContent(); // 登录载荷
        String deviceId = textMessage.getDeviceId(); // 从消息元数据中获取

        // 协议解析: 登录载荷格式 "userId:token"
        String[] parts = content.split(":", 2);
        if (parts.length != 2) {
            return CompletableFuture.completedFuture(AuthResult.failure("Invalid login payload format. Expected 'userId:token'."));
        }

        String userId = parts[0];
        String token = parts[1];

        // 模拟业务层的身份认证
        // 在真实项目中，这里会调用数据库或RPC服务来验证token
        if (AuthService.verify(userId, token)) {
            log.info("认证成功: userId={}, deviceId={}", userId, deviceId);
            // 认证成功，返回包含 userId 和 deviceId 的成功结果
            return CompletableFuture.completedFuture(AuthResult.success(userId, deviceId));
        } else {
            log.warn("认证失败: userId={}, token={}", userId, token);
            // 认证失败，返回包含错误信息的失败结果
            return CompletableFuture.completedFuture(AuthResult.failure("Invalid token"));
        }
    }
}
