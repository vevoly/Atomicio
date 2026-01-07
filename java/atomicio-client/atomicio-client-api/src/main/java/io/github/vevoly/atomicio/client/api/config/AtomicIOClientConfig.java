package io.github.vevoly.atomicio.client.api.config;

import io.github.vevoly.atomicio.client.api.constants.AtomicIOClientConstant;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * AtomicIO 客户端的配置类。
 * 支持链式调用进行设置。
 *
 * @since 0.5.0
 * @author vevoly
 */
@Data
@Accessors(chain = true)
public class AtomicIOClientConfig {

    /**
     * 服务器主机地址。
     */
    private String host = AtomicIOClientConstant.DEFAULT_HOST;

    /**
     * 服务器端口。
     */
    private int port = AtomicIOClientConstant.DEFAULT_PORT;

    /**
     * 连接超时时间（毫秒）。
     */
    private int connectTimeoutMillis = AtomicIOClientConstant.DEFAULT_CONNECT_TIMEOUT_MILLIS;

    // --- 心跳配置 ---
    /**
     * 是否启用客户端主动发送心跳。
     */
    private boolean heartbeatEnabled = AtomicIOClientConstant.DEFAULT_HEARTBEAT_ENABLED;

    /**
     * 写空闲时间（秒）。当客户端在该时间内没有发送任何数据时，会自动发送一个心跳包。
     * 必须大于0才能生效。
     */
    private int writerIdleSeconds = AtomicIOClientConstant.DEFAULT_WRITER_IDLE_SECONDS;

    // --- 重连配置 ---
    /**
     * 是否启用断线自动重连。
     */
    private boolean reconnectEnabled = AtomicIOClientConstant.DEFAULT_RECONNECT_ENABLED;

    /**
     * 初始重连延迟（秒）。
     */
    private int initialReconnectDelaySeconds = AtomicIOClientConstant.DEFAULT_INITIAL_RECONNECT_DELAY_SECONDS;

    /**
     * 最大重连延迟（秒）。重连延迟会以指数形式增长，直到达到此上限。
     */
    private int maxReconnectDelaySeconds = AtomicIOClientConstant.DEFAULT_MAX_RECONNECT_DELAY_SECONDS;
}
