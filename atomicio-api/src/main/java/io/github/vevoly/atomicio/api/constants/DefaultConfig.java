package io.github.vevoly.atomicio.api.constants;

import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterType;

/**
 * 默认配置常量
 *
 * @since 0.0.6
 * @author vevoly
 */
public class DefaultConfig {

    public static final String SYS_ID = "Atomicio";

    public static final String CONFIG_PREFIX = "atomicio";

    public static final String CLUSTER_CHANNEL_NAME = "atomicio-cluster-channel";

    public static final int DEFAULT_PORT = 8308;

    public static final int DEFAULT_BOSS_THREADS = 1;

    public static final int DEFAULT_WORKER_THREADS = 0;

    public static final boolean DEFAULT_CLUSTER_ENABLED = false;

    public static final String DEFAULT_CLUSTER_MODE = AtomicIOClusterType.REDIS.name();

}
