package io.github.vevoly.atomicio.server.api.constants;

/**
 * 默认配置常量
 *
 * @since 0.0.6
 * @author vevoly
 */
public class AtomicIOConstant {

    public static final String CLUSTER_CHANNEL_NAME = "atomicio-cluster-channel";
    public static final String IO_SESSION_KEY_NAME = "atomicio-session";

    // -- Netty Pipeline 名称 --
    public static final String PIPELINE_NAME_IP_CONNECTION_LIMIT_HANDLER = "ipConnectionLimitHandler";
    public static final String PIPELINE_NAME_SSL_HANDLER = "sslHandler";
    public static final String PIPELINE_NAME_SSL_EXCEPTION_HANDLER = "sslExceptionHandler";
    public static final String PIPELINE_NAME_ENCODER = "encoder";
    public static final String PIPELINE_NAME_DECODER = "decoder";
    public static final String PIPELINE_NAME_FRAME_DECODER = "frameDecoder";
    public static final String PIPELINE_NAME_IDLE_STATE_HANDLER = "idleStateHandler";
    public static final String PIPELINE_NAME_NETTY_EVENT_TRANSLATION_HANDLER = "nettyEventTranslationHandler";

    // -- Netty Pipeline 默认值 --

}
