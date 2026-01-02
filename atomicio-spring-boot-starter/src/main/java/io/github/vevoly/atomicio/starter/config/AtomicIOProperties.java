package io.github.vevoly.atomicio.starter.config;

import io.github.vevoly.atomicio.api.constants.DefaultConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AtomicIO 属性配置类
 *
 * @since 0.0.6
 * @author vevoly
 */
@Data
@ConfigurationProperties(prefix = DefaultConfig.CONFIG_PREFIX)
public class AtomicIOProperties {

    /**
     * 服务端口
     */
    private int port = DefaultConfig.DEFAULT_PORT;

    /**
     * Boss 线程数
     */
    private int bossThreads = DefaultConfig.DEFAULT_BOSS_THREADS;

    /**
     * 工人线程数，0 Netty 默认
     */
    private int workerThreads = DefaultConfig.DEFAULT_WORKER_THREADS;

    /**
     * 集群配置
     */
    private Cluster cluster = new Cluster();


    @Data
    public static class Cluster {

        /**
         * 是否开启集群模式
         */
        private boolean enabled = false;

        /**
         * 集群模式类型
         */
        private String type = DefaultConfig.DEFAULT_CLUSTER_MODE;

        /**
         * Redis 配置
         */
        private RedisProperties redis = new RedisProperties();

        /**
         * Kafka 配置
         */
        private KafkaProperties kafka = new KafkaProperties();

        /**
         * Rocket MQ 配置
         */
        private RocketMQProperties rocketmq = new RocketMQProperties();
    }

    @Data
    public static class RedisProperties {
        private String uri;
        private String username;
        private String password;
    }

    @Data
    public static class KafkaProperties {
        private String bootstrapServers;
        private String topic;
        private String consumerGroup;
    }

    @Data
    public static class RocketMQProperties {
        private String nameServer;
        private String topic;
        private String consumerGroup;
    }
}

