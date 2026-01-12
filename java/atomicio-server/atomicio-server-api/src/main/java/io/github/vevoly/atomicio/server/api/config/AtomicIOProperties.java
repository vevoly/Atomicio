package io.github.vevoly.atomicio.server.api.config;

import io.github.vevoly.atomicio.protocol.api.codec.AtomicIOCodecType;
import io.github.vevoly.atomicio.server.api.constants.AtomicIOConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AtomicIO 配置文件类
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
    private boolean enabled = false;

    /**
     * 服务端口
     * 默认值：8308
     */
    private int port = AtomicIOConstant.DEFAULT_PORT;

    /**
     * Boss 线程数
     * 默认值：1
     */
    private int bossThreads = AtomicIOConstant.DEFAULT_BOSS_THREADS;

    /**
     * 工人线程数，0 Netty 默认
     * 默认值：0
     */
    private int workerThreads = AtomicIOConstant.DEFAULT_WORKER_THREADS;

    /**
     * IP 安全配置
     */
    private IpSecurity ipSecurity = new IpSecurity();

    @Data
    public static class IpSecurity {

        /**
         * 每个 IP 最大连接数限制
         * 0 或负数 表示不限制
         */
        private int maxConnect = AtomicIOConstant.DEFAULT_MAX_CONNECT_LIMIT_PER_IP;

        /**
         * 单位时间内速率限制次数
         * 0 或负数 表示不限制
         */
        private int rateLimitCount = AtomicIOConstant.DEFAULT_RATE_LIMIT_PER_IP;

        /**
         * 速率限制的单位时间
         * 单位：秒
         */
        private int rateLimitInterval = AtomicIOConstant.DEFAULT_RATE_LIMIT_PERIOD_SECONDS;
    }

    /**
     * 服务器过载保护配置
     */
    private OverloadProtect overloadProtect = new OverloadProtect();

    @Data
    public static class OverloadProtect {

        /**
         * 是否开启服务器过载保护
         */
        private boolean enabled = true;

        /**
         * 单台服务器最大连接数
         */
        private int totalConnect = AtomicIOConstant.DEFAULT_OVERLOAD_TOTAL_CONNECT;

        /**
         * 单台服务器IO事件队列最容量告警阈值 (百分比)
         */
        private int queueMinPercent = AtomicIOConstant.DEFAULT_OVERLOAD_QUEUE_MIN_PERCENT;
    }

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

        /**
         * 是否允许多点登录
         * 默认为 false (单点登录)
         */
        private boolean multiLogin = false;

        /**
         * 读空闲时间
         * 服务器关心
         */
        private int readIdleSeconds = AtomicIOConstant.DEFAULT_READ_IDLE_SECONDS;

        /**
         * 写空闲时间
         * 服务器不关心，默认即可
         */
        private int writeIdleSeconds = AtomicIOConstant.DEFAULT_WRITE_IDLE_SECONDS;

        /**
         * 读写空闲时间
         */
        private int allIdleSeconds = AtomicIOConstant.DEFAULT_ALL_IDLE_SECONDS;
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

