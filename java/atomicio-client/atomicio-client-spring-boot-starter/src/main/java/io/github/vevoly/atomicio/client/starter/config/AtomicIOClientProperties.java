package io.github.vevoly.atomicio.client.starter.config;

import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.client.api.constants.AtomicIOClientConfigDefaultValue;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "atomicio.client")
public class AtomicIOClientProperties {

    /**
     * 是否启用 AtomicIO 客户端。
     */
    private boolean enabled = true;

    /**
     * 服务器主机地址。
     */
    private String serverHost = AtomicIOClientConfigDefaultValue.DEFAULT_SERVER_HOST;

    /**
     * 服务器端口。
     */
    private int serverPort = AtomicIOClientConfigDefaultValue.DEFAULT_SERVER_PORT;

    /**
     * 连接超时时间（毫秒）。
     */
    private int connectTimeoutMillis = AtomicIOClientConfigDefaultValue.DEFAULT_CONNECT_TIMEOUT_MILLIS;

    /**
     * 消息最大长度
     * 一定要与服务器端保持一致，否则会出现问题
     */
    private int maxFrameLength = AtomicIOClientConfigDefaultValue.DEFAULT_MAX_FRAME_LENGTH;

    // --- 心跳配置 ---
    /**
     * 是否启用客户端主动发送心跳。
     */
    private boolean heartbeatEnabled = AtomicIOClientConfigDefaultValue.DEFAULT_HEARTBEAT_ENABLED;

    /**
     * 写空闲时间（秒）。当客户端在该时间内没有发送任何数据时，会自动发送一个心跳包。
     * 必须大于0才能生效。
     */
    private int writerIdleSeconds = AtomicIOClientConfigDefaultValue.DEFAULT_WRITER_IDLE_SECONDS;

    // --- 重连配置 ---
    /**
     * 是否启用断线自动重连。
     */
    private boolean reconnectEnabled = AtomicIOClientConfigDefaultValue.DEFAULT_RECONNECT_ENABLED;

    /**
     * 初始重连延迟（秒）。
     */
    private int initialReconnectDelaySeconds = AtomicIOClientConfigDefaultValue.DEFAULT_INITIAL_RECONNECT_DELAY_SECONDS;

    /**
     * 最大重连延迟（秒）。重连延迟会以指数形式增长，直到达到此上限。
     */
    private int maxReconnectDelaySeconds = AtomicIOClientConfigDefaultValue.DEFAULT_MAX_RECONNECT_DELAY_SECONDS;

    /**
     * SSL/TLS 配置
     */
    private AtomicIOClientConfig.Ssl ssl = new AtomicIOClientConfig.Ssl();

    @Data
    public static class Ssl {

        /**
         * 默认 false， 关闭
         */
        private boolean enabled = false;

        /**
         * SSL 证书链文件路径
         */
        private String trustCertPath;

    }
}
