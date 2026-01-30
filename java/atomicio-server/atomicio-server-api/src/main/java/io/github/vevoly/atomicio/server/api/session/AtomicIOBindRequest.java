package io.github.vevoly.atomicio.server.api.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 封装了将会话绑定到用户时所需的所有上下文信息。
 *
 * @since 0.5.2
 * @author vevoly
 */
@Getter
@Builder
@AllArgsConstructor
public class AtomicIOBindRequest {

    /**
     * 用户 ID
     */
    private final String userId;

    /**
     * 设备 ID
     */
    private String deviceId; // 默认为 null

    /**
     * 设备类型。
     * 例如: "PC", "Web", "iOS", "Android"
     */
    private String deviceType;

    public AtomicIOBindRequest(String userId) {
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

}
