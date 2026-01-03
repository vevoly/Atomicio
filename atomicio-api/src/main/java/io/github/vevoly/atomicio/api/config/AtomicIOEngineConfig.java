package io.github.vevoly.atomicio.api.config;

import io.github.vevoly.atomicio.api.constants.AtomicIOConstant;
import lombok.Data;
import lombok.ToString;

/**
 * Atomicio 引擎的配置类。
 * 使用 Builder 模式来创建实例.
 *
 * @since 0.0.5
 * @author vevoly
 */
@Data
@ToString
public class AtomicIOEngineConfig {
    private int port = AtomicIOConstant.DEFAULT_PORT;
    private int bossThreads = AtomicIOConstant.DEFAULT_BOSS_THREADS;
    private int workerThreads = AtomicIOConstant.DEFAULT_WORKER_THREADS; // 0 代表 Netty 默认 (CPU核心数 * 2)

    private ClusterConfig clusterConfig = new ClusterConfig();

    @Data
    @ToString
    public static class ClusterConfig {
        private boolean enabled = AtomicIOConstant.DEFAULT_CLUSTER_ENABLED; // 默认不开启集群
        private String type = AtomicIOConstant.DEFAULT_CLUSTER_MODE;   // 默认集群类型
        private String redisUri;
        // todo 扩展 kafka, rocketmq 等配置
    }

}
