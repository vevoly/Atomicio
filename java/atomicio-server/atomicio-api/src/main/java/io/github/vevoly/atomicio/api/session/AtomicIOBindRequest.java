package io.github.vevoly.atomicio.api.session;

import lombok.Getter;

import java.util.Objects;

/**
 * 封装了将会话绑定到用户时所需的所有上下文信息。
 * 使用静态工厂方法创建，以提供灵活性和可读性。
 *
 * @since 0.5.2
 * @author vevoly
 */
@Getter
public class AtomicIOBindRequest {
    private final String userId;
    private String deviceId; // 默认为 null
    private String platform; // 默认为 null

    private AtomicIOBindRequest(String userId) {
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
    }

    // --- 静态工厂方法 ---
    /**
     * 创建一个最基础的绑定请求，只包含用户ID。
     */
    public static AtomicIOBindRequest of(String userId) {
        return new AtomicIOBindRequest(userId);
    }

    // --- 链式调用方法 (Fluent API) ---

    /**
     * 为绑定请求附加一个设备ID。
     */
    public AtomicIOBindRequest withDeviceId(String deviceId) {
        this.deviceId = deviceId;
        return this;
    }

    /**
     * 为绑定请求附加一个平台信息。
     */
    public AtomicIOBindRequest withPlatform(String platform) {
        this.platform = platform;
        return this;
    }
}
