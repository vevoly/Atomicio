package io.github.vevoly.atomicio.core.cluster;

import com.google.gson.Gson;

import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.api.constants.DefaultConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * Redis 集群通信提供者
 * @since 0.0.4
 * @author vevoly
 */
@Slf4j
public class RedisClusterProvider implements AtomicIOClusterProvider {

    private final Gson gson = new Gson();
    private RedisClient redisClient;

    private final String redisUri;

    // 发布消息的连接
    private StatefulRedisConnection<String, String> publishConnection;
    // 订阅消息的连接
    private StatefulRedisPubSubConnection<String, String> subscribeConnection;

    public RedisClusterProvider(String redisUri) {
        this.redisUri = redisUri;
    }

    @Override
    public void start() {
        log.info("Starting RedisClusterProvider with URI: {}", redisUri);
        try {
            this.redisClient = RedisClient.create(redisUri);
            this.publishConnection = redisClient.connect();
            this.subscribeConnection = redisClient.connectPubSub();
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
    public void publish(AtomicIOClusterMessage message) {
        try {
            String jsonMessage = gson.toJson(message);
            publishConnection.async().publish(DefaultConfig.CLUSTER_CHANNEL_NAME, jsonMessage);
            log.debug("Published cluster message: {}", jsonMessage);
        } catch (Exception e) {
            log.error("Failed to publish cluster message", e);
        }
    }

    @Override
    public void subscribe(Consumer<AtomicIOClusterMessage> listener) {
        subscribeConnection.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                if (DefaultConfig.CLUSTER_CHANNEL_NAME.equals(channel)) {
                    try {
                        log.debug("Received cluster message: {}", message);
                        AtomicIOClusterMessage clusterMessage = gson.fromJson(message, AtomicIOClusterMessage.class);
                        listener.accept(clusterMessage);
                    } catch (Exception e) {
                        log.error("Failed to process received cluster message", e);
                    }
                }
            }
        });

        RedisPubSubAsyncCommands<String, String> async = subscribeConnection.async();
        async.subscribe(DefaultConfig.CLUSTER_CHANNEL_NAME);
        log.info("Subscribed to Redis channel: {}", DefaultConfig.CLUSTER_CHANNEL_NAME);
    }
}
