package io.github.vevoly.atomicio.api.config;

import io.github.vevoly.atomicio.api.AtomicIOEngine;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterType;
import io.github.vevoly.atomicio.api.constants.DefaultConfig;
import lombok.AllArgsConstructor;

/**
 * AtomicIOConfig 的构建器
 *
 * @since 0.0.5
 * @author vevoly
 */
@AllArgsConstructor
public class AtomicIOConfigBuilder {

    private final AtomicIOEngineConfig config;

    public AtomicIOConfigBuilder() {
        this.config = new AtomicIOEngineConfig();
    }

    public AtomicIOConfigBuilder port(int port) {
        config.setPort(port);
        return this;
    }

    public AtomicIOConfigBuilder workerThreads(int threads) {
        config.setWorkerThreads(threads);
        return this;
    }

    public AtomicIOConfigBuilder enableClusterWithRedis(String redisUri) {
        config.getClusterConfig().setEnabled(DefaultConfig.DEFAULT_CLUSTER_ENABLED);
        config.getClusterConfig().setType(AtomicIOClusterType.REDIS.name());
        config.getClusterConfig().setRedisUri(redisUri);
        return this;
    }

    public AtomicIOConfigBuilder disableCluster() {
        config.getClusterConfig().setEnabled(false);
        return this;
    }

    /**
     * 根据当前配置构建最终的引擎实例。
     * @return AtomicIOEngine 实例
     */
    public AtomicIOEngine build() {
        try {
            Class<?> engineClass = Class.forName("io.atomic.core.engine.DefaultAtomicIOEngine");
            return (AtomicIOEngine) engineClass.getConstructor(AtomicIOEngineConfig.class).newInstance(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build AtomicIOEngine. Is atomicio-core on the classpath?", e);
        }
    }


}
