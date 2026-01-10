package io.github.vevoly.atomicio.core.cluster;

import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.api.constants.AtomicIOConstant;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Redis 集群通信提供者
 * @since 0.5.9
 * @author vevoly
 */
@Slf4j
public class RedisClusterProvider implements AtomicIOClusterProvider {

    private final String redisUri;
    private RedisClient redisClient;

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

    public RedisClusterProvider(String redisUri) {
        this.redisUri = redisUri;
    }

    @Override
    public void start() {
        log.info("Starting RedisClusterProvider with URI: {}", redisUri);
        try {
            this.redisClient = RedisClient.create(redisUri);
            this.publishConnection = redisClient.connect(STRING_REDIS_CODEC);
            this.subscribeConnection = redisClient.connectPubSub(STRING_REDIS_CODEC);
            // 验证连接可用性
            try (StatefulRedisConnection<String, String> connection = redisClient.connect(StringCodec.UTF8)) {
                String pong = connection.sync().ping();
                if (!"PONG".equalsIgnoreCase(pong)) {
                    throw new IllegalStateException("Redis PING command failed, received: ".concat(pong));
                }
                log.info("Redis connection verified successfully.");
            }
        } catch (RedisException e) {
            log.error("Failed to connect to Redis at {}. Please check your Redis server and configuration.", redisUri, e);
            throw new IllegalStateException("Cannot start RedisClusterProvider: Failed to connect to Redis.", e);
        }
    }

    @Override
    public void shutdown() {
        log.info("Shutting down RedisClusterProvider...");
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
            publishConnection.async().publish(AtomicIOConstant.CLUSTER_CHANNEL_NAME, data);
        } catch (Exception e) {
            log.error("Failed to publish cluster message", e);
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
                if (AtomicIOConstant.CLUSTER_CHANNEL_NAME.equals(channel)) {
                    try {
                        dataConsumer.accept(message);
                    } catch (Exception e) {
                        log.error("Failed to process received cluster message", e);
                    }
                }
            }
        });

        RedisPubSubAsyncCommands<String, byte[]> async = subscribeConnection.async();
        async.subscribe(AtomicIOConstant.CLUSTER_CHANNEL_NAME);
        log.info("Subscribed to Redis channel: {}", AtomicIOConstant.CLUSTER_CHANNEL_NAME);
    }
}
