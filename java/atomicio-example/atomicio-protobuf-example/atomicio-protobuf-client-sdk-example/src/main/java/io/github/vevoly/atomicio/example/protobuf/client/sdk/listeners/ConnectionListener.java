package io.github.vevoly.atomicio.example.protobuf.client.sdk.listeners;

import io.github.vevoly.atomicio.client.api.AtomicIOClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 客户端连接监听器
 */
@Slf4j
@RequiredArgsConstructor
public class ConnectionListener {

    private final AtomicIOClient client;

    /**
     * 当连接成功建立时调用。
     * 这是发起登录请求的最佳时机。
     */
    public void onConnected(Void v) {
        log.info(">>>>>>>>> 成功连接到服务器! <<<<<<<<<");

        // 自动发送登录请求
        String userId = "user_" + ThreadLocalRandom.current().nextInt(1000, 9999);
        String deviceId = "sdk-device-" + userId;

        // 在新线程中执行登录，避免阻塞 Netty 的 I/O 线程
        // (虽然 .thenApply 等是异步的，但发起动作本身最好另起线程)
        new Thread(() -> {
            client.login(userId, "any-valid-token", deviceId)
                    .thenAccept(authResult -> {
                        if (authResult.success()) {
                            log.info("<<<<<<<<< 登录成功! {}", authResult.errorMessage());
                            // 可以在这里触发“登录成功”的业务事件，
                            // 例如通知 UI 更新，或者启动心跳之外的其他业务轮询
                        } else {
                            log.error("<<<<<<<<< 登录失败: {}", authResult.errorMessage());
                            client.disconnect();
                        }
                    })
                    .exceptionally(ex -> {
                        log.error("登录请求在执行中发生异常", ex);
                        client.disconnect();
                        return null;
                    });
        }).start();
    }

    /**
     * 当连接断开时调用。
     */
    public void onDisconnected(Void v) {
        log.warn(">>>>>>>>> 与服务器断开连接. <<<<<<<<<");
        // 可以在这里更新 UI 状态，显示“已断开”
        // 或者触发清理逻辑
    }

    /**
     * 当正在重连时调用。
     */
    public void onReconnecting(int attempt, int delay) {
        log.info(">>>>>>>>> 正在进行第 {} 次重连，将在 {} 秒后尝试... <<<<<<<<<", attempt, delay);
    }
}
