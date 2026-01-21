package io.github.vevoly.atomicio.client.api.constants;

/**
 * 客户端配置文件默认值
 *
 * @since 0.6.6
 * @author vevoly
 */
public class AtomicIOClientConfigDefaultValue {



    public static final int DEFAULT_PORT = 8401;
    public static final String DEFAULT_SERVER_HOST = "127.0.0.1";
    public static final int DEFAULT_SERVER_PORT = 8308;
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;
    public static final boolean DEFAULT_HEARTBEAT_ENABLED = true;
    public static final int DEFAULT_WRITER_IDLE_SECONDS = 15;
    public static final int DEFAULT_MAX_FRAME_LENGTH = 65536; // 与服务器端保持一致

    public static final boolean DEFAULT_RECONNECT_ENABLED = true;
    public static final int DEFAULT_INITIAL_RECONNECT_DELAY_SECONDS = 1;
    public static final int DEFAULT_MAX_RECONNECT_DELAY_SECONDS = 60;
}
