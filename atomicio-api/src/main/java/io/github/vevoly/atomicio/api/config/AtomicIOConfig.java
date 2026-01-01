package io.github.vevoly.atomicio.api.config;

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
public class AtomicIOConfig {
    private int port = 8308;
    private int bossThreads = 1;
    private int workerThreads = 0; // 0 代表 Netty 默认 (CPU核心数 * 2)

    private ClusterConfig clusterConfig = new ClusterConfig(); // 默认启用，但配置为空

    @Data
    @ToString
    public static class ClusterConfig {
        private boolean enabled = false; // 默认不开启集群
        private String type = "redis";   // 默认集群类型
        private String redisUri;         // redis://...
        // todo 扩展 kafka, rocketmq 等配置
    }

}
