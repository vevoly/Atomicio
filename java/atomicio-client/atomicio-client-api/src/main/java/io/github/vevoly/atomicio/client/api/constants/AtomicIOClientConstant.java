package io.github.vevoly.atomicio.client.api.constants;


/**
 * 客户端默认配置常量
 *
 * @since 0.5.0
 * @author vevoly
 */
public class AtomicIOClientConstant {

    // -- 配置文件默认值 --
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8308;
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;
    public static final boolean DEFAULT_HEARTBEAT_ENABLED = true;
    public static final int DEFAULT_WRITER_IDLE_SECONDS = 15;

    // -- 重连配置默认值 --
    public static final boolean DEFAULT_RECONNECT_ENABLED = true;
    public static final int DEFAULT_INITIAL_RECONNECT_DELAY_SECONDS = 1;
    public static final int DEFAULT_MAX_RECONNECT_DELAY_SECONDS = 60;


}
