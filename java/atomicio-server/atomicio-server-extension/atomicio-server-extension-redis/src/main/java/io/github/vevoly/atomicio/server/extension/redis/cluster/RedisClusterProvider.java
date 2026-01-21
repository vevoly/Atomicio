package io.github.vevoly.atomicio.server.extension.redis.cluster;

import io.github.vevoly.atomicio.common.api.config.AtomicIOConfigDefaultValue;
import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.common.api.constants.AtomicIOConstant;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.server.api.constants.AtomicIOServerConstant;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Redis 集群通信提供者
 * @since 0.5.9
 * @author vevoly
 */
@Slf4j
public class RedisClusterProvider implements AtomicIOClusterProvider {

    private AtomicIOProperties.Cluster clusterConfig;
    private RedisClient redisClient;
    private String clusterNodeId;

    // 定义 Codec 实例
    private static final RedisCodec<String, byte[]> STRING_REDIS_CODEC = new RedisCodec<>() {

        @Override
        public String decodeKey(ByteBuffer byteBuffer) {
            return StandardCharsets.UTF_8.decode(byteBuffer).toString();
        }

        @Override
        public byte[] decodeValue(ByteBuffer byteBuffer) {
            byte[] array = new byte[byteBuffer.remaining()];
            byteBuffer.get(array);
            return array;
        }

        @Override
        public ByteBuffer encodeKey(String key) {
            return StandardCharsets.UTF_8.encode(key);
        }

        @Override
        public ByteBuffer encodeValue(byte[] bytes) {
            return ByteBuffer.wrap(bytes);
        }
    };

    // 发布连接：Key 是 String (频道名)，Value 是 byte[] (消息内容)
    private StatefulRedisConnection<String, byte[]> publishConnection;
    // 订阅连接：同上
    private StatefulRedisPubSubConnection<String, byte[]> subscribeConnection;

    public RedisClusterProvider(AtomicIOProperties.Cluster clusterConfig, RedisClient redisClient) {
        this.clusterConfig = clusterConfig;
        this.redisClient = redisClient;
        String configNodeId = clusterConfig.getNodeId();
        if (configNodeId == null || AtomicIOConfigDefaultValue.DEFAULT_NODE_ID.equals(configNodeId)) {
            this.clusterNodeId = AtomicIOConfigDefaultValue.CONFIG_PREFIX_NODE_ID + UUID.randomUUID().toString().substring(0, 8);
        } else {
            this.clusterNodeId = configNodeId;
        }
        log.info("当前节点身份标识 nodeId: {}", this.clusterNodeId);
    }

    @Override
    public String getCurrentNodeId() {
        return this.clusterNodeId;
    }

    @Override
    public void start() {
        log.info("正在启动 RedisClusterProvider ... ");
        try {
            this.publishConnection = redisClient.connect(STRING_REDIS_CODEC);
            this.subscribeConnection = redisClient.connectPubSub(STRING_REDIS_CODEC);
            // 验证连接
            try (StatefulRedisConnection<String, String> connection = redisClient.connect(StringCodec.UTF8)) {
                String pong = connection.sync().ping();
                if (!AtomicIOConstant.DEFAULT_HEARTBEAT_RESPONSE.equalsIgnoreCase(pong)) {
                    throw new IllegalStateException("Redis PING 命令失败，收到响应: ".concat(pong));
                }
                log.info("RedisStateProvider 已启动，连接验证成功。");
            }
        } catch (RedisException e) {
            log.error("无法连接到 Redis 地址 {}。请检查 Redis 服务器状态及配置。", clusterConfig.getRedis().getUri(), e);
            throw new IllegalStateException("无法启动 RedisStateProvider: 连接 Redis 失败。", e);
        }
    }

    @Override
    public void shutdown() {
        log.info("正在关闭 RedisClusterProvider...");
        if (publishConnection != null) {
            publishConnection.close();
        }
        if (subscribeConnection != null) {
            subscribeConnection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    @Override
    public void publish(byte[] data) {
        try {
            publishConnection.async().publish(AtomicIOServerConstant.CLUSTER_CHANNEL_NAME, data);
        } catch (Exception e) {
            log.error("Failed to publish cluster message", e);
        }
    }

    @Override
    public void publishKickOut(String nodeId, byte[] data) {
        try {
            publishConnection.async().publish(AtomicIOServerConstant.KICK_OUT_CHANNEL_PREFIX_NAME + nodeId, data);
        } catch (Exception e) {
            log.error("Failed to publishKickOut cluster message", e);
        }
    }

    @Override
    public void subscribe(Consumer<byte[]> dataConsumer) {
        if (subscribeConnection == null) {
            return;
        }
        subscribeConnection.addListener(new RedisPubSubAdapter<String, byte[]>() {
            @Override
            public void message(String channel, byte[] message) {
                if (AtomicIOServerConstant.CLUSTER_CHANNEL_NAME.equals(channel)) {
                    try {
                        dataConsumer.accept(message);
                    } catch (Exception e) {
                        log.error("Failed to process received cluster message", e);
                    }
                }
            }
        });

        subscribeConnection.async().subscribe(AtomicIOServerConstant.CLUSTER_CHANNEL_NAME);
        log.info("已成功订阅集群消息频道: {}", AtomicIOServerConstant.CLUSTER_CHANNEL_NAME);
    }

    /**
     * 订阅属于本节点的踢人通知
     * @param kickOutConsumer 处理踢出逻辑的回调 (接收消息格式：userId:deviceId)
     */
    @Override
    public void subscribeKickOut(Consumer<byte[]> kickOutConsumer) {
        if (subscribeConnection == null) {
            return;
        }
        String kickOutChannel = AtomicIOServerConstant.KICK_OUT_CHANNEL_PREFIX_NAME + this.clusterNodeId;

        // 添加监听器：只负责分发原始字节
        subscribeConnection.addListener(new RedisPubSubAdapter<String, byte[]>() {
            @Override
            public void message(String channel, byte[] message) {
                // 只有当频道名完全匹配时才处理
                if (kickOutChannel.equals(channel)) {
                    try {
                        kickOutConsumer.accept(message);
                    } catch (Exception e) {
                        log.error("执行踢出回调逻辑失败", e);
                    }
                }
            }
        });

        // 执行异步订阅
        subscribeConnection.async().subscribe(kickOutChannel)
                .thenAccept(v -> log.info("已成功开启本节点踢出频道订阅: {}", kickOutChannel))
                .exceptionally(e -> {
                    log.error("订阅踢出频道失败: {}", kickOutChannel, e);
                    return null;
                });
    }
}
