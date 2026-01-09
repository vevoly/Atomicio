package io.github.vevoly.atomicio.api.constants;

import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterType;

/**
 * 默认配置常量
 *
 * @since 0.0.6
 * @author vevoly
 */
public class AtomicIOConstant {

    public static final String SYS_ID = "Atomicio";

    public static final String CONFIG_PREFIX = "atomicio";

    public static final String CLUSTER_CHANNEL_NAME = "atomicio-cluster-channel";

    // -- Netty Pipeline 名称 --
    public static final String PIPELINE_NAME_SSL_HANDLER = "sslHandler";
    public static final String PIPELINE_NAME_SSL_EXCEPTION_HANDLER = "sslExceptionHandler";
    public static final String PIPELINE_NAME_ENCODER = "encoder";
    public static final String PIPELINE_NAME_DECODER = "decoder";
    public static final String PIPELINE_NAME_FRAME_DECODER = "frameDecoder";
    public static final String PIPELINE_NAME_IDLE_STATE_HANDLER = "idleStateHandler";
    public static final String PIPELINE_NAME_CHANNEL_HANDLER = "channelHandler";

    // -- Netty Pipeline 默认值 --


    // -- 配置文件默认值 --
    public static final boolean DEFAULT_ENABLED = false;
    public static final int DEFAULT_PORT = 8308;
    public static final int DEFAULT_BOSS_THREADS = 1;
    public static final int DEFAULT_WORKER_THREADS = 0;
    public static final int DEFAULT_MAX_FRAME_LENGTH = 65536; // 64KB
    public static final boolean DEFAULT_CLUSTER_ENABLED = false;
    public static final String DEFAULT_CLUSTER_MODE = AtomicIOClusterType.REDIS.name();


}
