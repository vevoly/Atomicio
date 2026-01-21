package io.github.vevoly.atomicio.core.manager;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessageType;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterProvider;
import io.github.vevoly.atomicio.server.api.codec.AtomicIOServerCodecProvider;
import io.github.vevoly.atomicio.server.api.manager.ClusterManager;
import io.github.vevoly.atomicio.server.api.manager.DisruptorManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * 集群管理器
 * 负责消息的序列化（Kryo）、集群频道的管理、以及消息在节点间的路由。
 *
 * @since 0.5.9
 * @author vevoly
 */
@Slf4j
public class AtomicIOClusterManager implements ClusterManager {

    private final AtomicIOProperties config;
    private final AtomicIOClusterProvider clusterProvider;
    private final AtomicIOServerCodecProvider codecProvider;
    private final DisruptorManager disruptorManager;

    private final Kryo kryo;

    public AtomicIOClusterManager(
            AtomicIOProperties config,
            @Nullable AtomicIOClusterProvider provider,
            AtomicIOServerCodecProvider codecProvider,
            DisruptorManager disruptorManager) {
        this.config = config;
        this.clusterProvider = provider;
        this.codecProvider = codecProvider;
        this.disruptorManager = disruptorManager;
        // 初始化 Kryo
        this.kryo = new Kryo();
        // 注册需要序列化的类
        kryo.register(AtomicIOClusterMessage.class);
        kryo.register(java.util.HashSet.class);
        kryo.register(AtomicIOClusterMessageType.class);
        kryo.register(byte[].class);
    }

    /**
     * 启动集群管理器
     * 内部会启动并订阅底层 Provider
     */
    @Override
    public void start() {
        if (clusterProvider != null) {
            clusterProvider.start();
            // 订阅原始字节，然后在这里反序列化和分发
            clusterProvider.subscribe(this::handleReceivedData);
            clusterProvider.subscribeKickOut(this::handleReceivedData);
        }
    }

    @Override
    public void shutdown() {
        if (clusterProvider != null) {
            clusterProvider.shutdown();
        }
    }

    @Override
    public String getCurrentNodeId() {
        return clusterProvider.getCurrentNodeId();
    }

    /**
     * 接收来自 Engine 的 POJO，序列化后发布
     * 替代 provider.publish
     * @param message
     */
    @Override
    public void publish(AtomicIOClusterMessage message) {
        if (clusterProvider == null) {
            return;
        }
        // 序列化
        byte[] msg = serialize(message);
        // 调用底层 provider 发送原始字节
        clusterProvider.publish(msg);
    }

    @Override
    public void publishKickOut(String nodeId, AtomicIOClusterMessage message) {
        if (clusterProvider == null) {
            return;
        }
        // 序列化
        byte[] msg = serialize(message);
        // 调用底层 provider 发送原始字节
        clusterProvider.publishKickOut(nodeId, msg);
    }

    @Override
    public AtomicIOClusterMessage buildClusterMessage(AtomicIOMessage message, AtomicIOClusterMessageType messageType, String target, Set<String> excludeUserIds) {
        // 1. 预编码
        byte[] finalPayload = new byte[0];
        try {
            finalPayload = codecProvider.encodeToBytes(message, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        AtomicIOClusterMessage clusterMessage = new AtomicIOClusterMessage();
        clusterMessage.setMessageType(messageType);
        clusterMessage.setCommandId(message.getCommandId());
        // 2. payload 存储的是“预编码”后的最终字节
        clusterMessage.setPayload(finalPayload);
        if (target != null) {
            clusterMessage.setTarget(target);
        }
        if (excludeUserIds != null && excludeUserIds.size() > 0) {
            HashSet<String> excludeUserIdSet = new HashSet<>(Set.copyOf(excludeUserIds));
            clusterMessage.setExcludeUserIds(excludeUserIdSet);
        }
        return clusterMessage;
    }

    /**
     * 将集群消息序列化为字节数组
     */
    public byte[] serialize(AtomicIOClusterMessage message) {
        if (message == null) return new byte[0];
        // Kryo 非线程安全，建议在方法内 new 或者使用 ThreadLocal
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Output output = new Output(baos)) {
            kryo.writeObject(output, message);
            output.close();
            return baos.toByteArray();
        }
    }

    /**
     * 接收来自 Provider 的原始字节，反序列化后交给 Disruptor
     * @param data
     */
    private void handleReceivedData(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        try (Input input = new Input(data)) {
            AtomicIOClusterMessage message = kryo.readObject(input, AtomicIOClusterMessage.class);
            input.close();
            // 将反序列化后的 POJO 发布到 Disruptor
            disruptorManager.publish(disruptorEntry -> disruptorEntry.setClusterMessage(message));
        } catch (Exception e) {
            log.error("反序列化集群消息失败", e);
        }
    }

}
