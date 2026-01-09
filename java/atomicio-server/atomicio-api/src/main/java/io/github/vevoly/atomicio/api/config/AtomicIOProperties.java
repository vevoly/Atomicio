package io.github.vevoly.atomicio.api.config;

import io.github.vevoly.atomicio.api.codec.AtomicIOCodecType;
import io.github.vevoly.atomicio.api.constants.AtomicIOConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AtomicIO 属性配置类
 *
 * @since 0.0.6
 * @author vevoly
 */
@Data
@ConfigurationProperties(prefix = AtomicIOConstant.CONFIG_PREFIX)
public class AtomicIOProperties {

    /**
     * 是否开启 AtomicIO 引擎
     */
    private boolean enabled = AtomicIOConstant.DEFAULT_ENABLED;

    /**
     * 服务端口
     */
    private int port = AtomicIOConstant.DEFAULT_PORT;

    /**
     * Boss 线程数
     */
    private int bossThreads = AtomicIOConstant.DEFAULT_BOSS_THREADS;

    /**
     * 工人线程数，0 Netty 默认
     */
    private int workerThreads = AtomicIOConstant.DEFAULT_WORKER_THREADS;

    /**
     * 解码器配置
     */
    private Codec codec = new Codec();

    @Data
    public static class Codec {

        /**
         * 默认解码器为 TEXT 简单文本解码器
         * 可选解码器请查看 AtomicIOCodecType 枚举类
         */
        private String type = AtomicIOCodecType.TEXT.name();

        /**
         * 消息最大长度
         * 防御 Ddos 攻击 （应用层防御）
         */
        private int maxFrameLength = AtomicIOConstant.DEFAULT_MAX_FRAME_LENGTH;
    }

    /**
     * 会话配置
     */
    private Session session = new Session();

    @Data
    public static class Session {
        private boolean multiLogin = false; // 默认为 false (单点登录)
    }

    /**
     * 集群配置
     */
    private Cluster cluster = new Cluster();

    @Data
    public static class Cluster {

        /**
         * 是否开启集群模式
         */
        private boolean enabled = false; // 默认 false 关闭集群

        /**
         * 集群模式类型
         */
        private String type = AtomicIOConstant.DEFAULT_CLUSTER_MODE;

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

    /**
     * SSL/TLS 配置
     */
    private Ssl ssl = new Ssl();

    @Data
    public static class Ssl {
        private boolean enabled = false;   // 默认 false 为关闭
        private String certChainPath;      // SSL 证书链文件路径
        private String privateKeyPath;     // private key 文件路径
        private String privateKeyPassword; // private key 的密码
    }
}

