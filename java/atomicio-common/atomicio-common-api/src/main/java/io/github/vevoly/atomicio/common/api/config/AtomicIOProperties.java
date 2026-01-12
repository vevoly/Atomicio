package io.github.vevoly.atomicio.common.api.config;

import io.github.vevoly.atomicio.protocol.api.codec.AtomicIOCodecType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AtomicIO 配置文件类
 *
 * @since 0.0.6
 * @author vevoly
 */
@Data
@ConfigurationProperties(prefix = AtomicIOConfigDefaultValue.CONFIG_PREFIX)
public class AtomicIOProperties {

    /**
     * 是否开启 AtomicIO 引擎
     */
    private boolean enabled = false;

    /**
     * 服务端口
     * 默认值：8308
     */
    private int port = AtomicIOConfigDefaultValue.DEFAULT_PORT;

    /**
     * Boss 线程数
     * 默认值：1
     */
    private int bossThreads = AtomicIOConfigDefaultValue.DEFAULT_BOSS_THREADS;

    /**
     * 工人线程数，0 Netty 默认
     * 默认值：0
     */
    private int workerThreads = AtomicIOConfigDefaultValue.DEFAULT_WORKER_THREADS;

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
        private int maxConnect = AtomicIOConfigDefaultValue.DEFAULT_MAX_CONNECT_LIMIT_PER_IP;

        /**
         * 单位时间内速率限制次数
         * 0 或负数 表示不限制
         */
        private int rateLimitCount = AtomicIOConfigDefaultValue.DEFAULT_RATE_LIMIT_PER_IP;

        /**
         * 速率限制的单位时间
         * 单位：秒
         */
        private int rateLimitInterval = AtomicIOConfigDefaultValue.DEFAULT_RATE_LIMIT_PERIOD_SECONDS;
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
        private int totalConnect = AtomicIOConfigDefaultValue.DEFAULT_OVERLOAD_TOTAL_CONNECT;

        /**
         * 单台服务器IO事件队列最容量告警阈值 (百分比)
         */
        private int queueMinPercent = AtomicIOConfigDefaultValue.DEFAULT_OVERLOAD_QUEUE_MIN_PERCENT;
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
        private int maxFrameLength = AtomicIOConfigDefaultValue.DEFAULT_MAX_FRAME_LENGTH;
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
        private int readIdleSeconds = AtomicIOConfigDefaultValue.DEFAULT_READ_IDLE_SECONDS;

        /**
         * 写空闲时间
         * 服务器不关心，默认即可
         */
        private int writeIdleSeconds = AtomicIOConfigDefaultValue.DEFAULT_WRITE_IDLE_SECONDS;

        /**
         * 读写空闲时间
         */
        private int allIdleSeconds = AtomicIOConfigDefaultValue.DEFAULT_ALL_IDLE_SECONDS;
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
        private String type = AtomicIOConfigDefaultValue.DEFAULT_CLUSTER_MODE;

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

    /**
     * ID 生成配置
     */
    private IdGen idGen = new IdGen();

    @Data
    public static class IdGen {

        /**
         * ID 生成器类型
         * 默认：框架集成雪花算法
         * 设置为 ‘custom’ 以使用用户自定义的工厂类
         */
        private String type = AtomicIOConfigDefaultValue.DEFAULT_ID_GEN_TYPE;

        /**
         * 如果类型（type）为 'custom'，请指定 IdGeneratorFactory 实现类的全限定类名。
         * 该工厂类必须包含一个公共的无参构造函数。
         */
        private String customFactoryClass;

        /**
         * 针对 'snowflake'（雪花算法）类型的特定配置。
         */
        private Snowflake snowflake = new Snowflake();
    }

    @Data
    public static class Snowflake {
        /**
         * 雪花算法的起始时间戳（毫秒级）。
         * 这是计算 ID 时间戳部分的起点。
         * 建议将其设置为最近的日期，以延长生成器的可用年限。
         * 默认值为 1704067200000L (2024-01-01T00:00:00Z)。
         */
        private long epoch = AtomicIOConfigDefaultValue.DEFAULT_ID_GEN_SNOWFLAKE_EPOCH;

        /**
         * 雪花算法的工作机器 ID。
         * 取值范围必须在 0 到 31 之间。
         * 在集群部署中，每个应用实例在所属数据中心内必须拥有唯一的工作机器 ID。
         * 建议通过环境变量或启动脚本动态分配此值。
         */
        private long workerId = AtomicIOConfigDefaultValue.DEFAULT_ID_GEN_SNOWFLAKE_WORKER_ID;

        /**
         * 雪花算法的数据中心 ID。
         * 取值范围必须在 0 到 31 之间。
         */
        private long datacenterId = AtomicIOConfigDefaultValue.DEFAULT_ID_GEN_SNOWFLAKE_DATACENTER_ID;
    }
}

