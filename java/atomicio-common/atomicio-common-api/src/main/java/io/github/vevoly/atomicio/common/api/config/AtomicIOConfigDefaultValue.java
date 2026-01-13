package io.github.vevoly.atomicio.common.api.config;

import io.github.vevoly.atomicio.common.api.id.AtomicIOIdGeneratorType;

public class AtomicIOConfigDefaultValue {

    public static final String SYS_ID = "Atomicio";

    public static final String CONFIG_PREFIX = "atomicio";
    public static final String CONFIG_PREFIX_ID_GEN = CONFIG_PREFIX + ".id-gen";
    public static final String CONFIG_PREFIX_CODEC = CONFIG_PREFIX + ".codec";

    // -- 配置文件默认值 --
    public static final int DEFAULT_PORT = 8308;
    public static final int DEFAULT_BOSS_THREADS = 1;
    public static final int DEFAULT_WORKER_THREADS = 0;
    public static final int DEFAULT_MAX_FRAME_LENGTH = 65536; // 64KB
    public static final int DEFAULT_MAX_CONNECT_LIMIT_PER_IP = 10;
    public static final int DEFAULT_RATE_LIMIT_PER_IP = 100;
    public static final int DEFAULT_RATE_LIMIT_PERIOD_SECONDS = 60;
    public static final int DEFAULT_OVERLOAD_TOTAL_CONNECT = 100000;
    public static final int DEFAULT_OVERLOAD_QUEUE_MIN_PERCENT = 20;
    public static final String DEFAULT_CLUSTER_MODE = "redis";
    public static final int DEFAULT_READ_IDLE_SECONDS = 30;
    public static final int DEFAULT_WRITE_IDLE_SECONDS = 0;
    public static final int DEFAULT_ALL_IDLE_SECONDS = 0;
    public static final String DEFAULT_ID_GEN_TYPE = AtomicIOIdGeneratorType.SNOWFLAKE.name();
    public static final long DEFAULT_ID_GEN_SNOWFLAKE_EPOCH = 1704067200000L;
    public static final int DEFAULT_ID_GEN_SNOWFLAKE_WORKER_ID = 0;
    public static final int DEFAULT_ID_GEN_SNOWFLAKE_DATACENTER_ID = 0;
}
