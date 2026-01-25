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
import io.github.vevoly.atomicio.server.api.constants.AtomicIOServerConstant;
import io.github.vevoly.atomicio.server.api.manager.ClusterManager;
import io.github.vevoly.atomicio.server.api.manager.DisruptorManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.*;

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

    // 线程安全 初始化 Kryo
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.register(AtomicIOClusterMessage.class, 100);
        kryo.register(java.util.ArrayList.class, 101);
        kryo.register(java.util.HashSet.class, 102);
        kryo.register(AtomicIOClusterMessageType.class);
        kryo.register(byte[].class);
        return kryo;
    });

    // 在 ThreadLocal 中同时复用 Kryo 和 Output 缓冲区
    private static final ThreadLocal<Output> outputThreadLocal = ThreadLocal.withInitial(() -> new Output(1024, -1));

    public AtomicIOClusterManager(
            AtomicIOProperties config,
            @Nullable AtomicIOClusterProvider provider,
            AtomicIOServerCodecProvider codecProvider,
            DisruptorManager disruptorManager) {
        this.config = config;
        this.clusterProvider = provider;
        this.codecProvider = codecProvider;
        this.disruptorManager = disruptorManager;
    }

    /**
     * 启动集群管理器
     * 内部会启动并订阅底层 Provider
     */
    @Override
    public void start() {
        if (clusterProvider != null) {
            clusterProvider.start();
            // 订阅频道
            clusterProvider.subscribe(this::handleReceivedData,
                    AtomicIOServerConstant.CLUSTER_TOPIC_GLOBAL,
                    AtomicIOServerConstant.clusterTopicForNode(getCurrentNodeId()));
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
        byte[] data = serialize(message);
        // 调用底层 provider 发送原始字节
        clusterProvider.publish(AtomicIOServerConstant.CLUSTER_TOPIC_GLOBAL, data);
    }

    @Override
    public void publishToNode(String targetNodeId, AtomicIOClusterMessage message) {
        if (clusterProvider == null) {
            return;
        }
        byte[] data = serialize(message);
        clusterProvider.publish(AtomicIOServerConstant.clusterTopicForNode(targetNodeId), data);
    }

    @Override
    public AtomicIOClusterMessage buildClusterMessage(AtomicIOMessage message, AtomicIOClusterMessageType messageType, Object target, Set<String> excludeUserIds) {
        byte[] finalPayload = new byte[0];
        if (message.getPayload() != null) {
            try {
                finalPayload = codecProvider.encodeToBytes(message, config);
            } catch (Exception e) {
                log.error("Payload encode error", e);
                return null;
            }
        }

        AtomicIOClusterMessage clusterMessage = new AtomicIOClusterMessage();
        clusterMessage.setMessageType(messageType);
        clusterMessage.setCommandId(message.getCommandId());
        clusterMessage.setPayload(finalPayload);
        clusterMessage.setExcludeUserIds(excludeUserIds);

        // 根据消息类型设置不同的目标字段
        switch (messageType) {
            case SEND_TO_USER:
                clusterMessage.setTargetUserId((String) target);
                break;
            case SEND_TO_GROUP:
                clusterMessage.setTargetGroupId((String) target);
                break;
            case SEND_TO_USERS_BATCH:
                processBatchTarget(clusterMessage, target);
                break;
            case BROADCAST:
                // 广播消息不需要 target
                break;
            case KICK_OUT:
                clusterMessage.setTargetUserId((String) target);
                break;
            default:
                log.warn("Unknown message type: {}. Message will be dropped.", messageType);
                return null; // 丢弃消息
        }
        return clusterMessage;
    }

    private void processBatchTarget(AtomicIOClusterMessage msg, Object target) {
        if (target instanceof List) {
            msg.setTargetUserIds((List<String>) target);
        } else if (target instanceof Collection) {
            msg.setTargetUserIds(new ArrayList<>((Collection<String>) target));
        }
    }

    /**
     * 将集群消息序列化为字节数组
     */
    public byte[] serialize(AtomicIOClusterMessage message) {
        if (message == null) return new byte[0];
        Kryo kryo = kryoThreadLocal.get(); // 线程安全
        Output output = outputThreadLocal.get();
        output.reset(); // 重置指针，复用缓冲区
        kryo.writeObject(output, message);
        return output.toBytes();
    }

    /**
     * 接收来自 Provider 的原始字节，反序列化后交给 Disruptor
     * @param data
     */
    private void handleReceivedData(byte[] data) {
        if (data == null || data.length == 0) return;
        Kryo kryo = kryoThreadLocal.get();
        try (Input input = new Input(data)) {
            AtomicIOClusterMessage message = kryo.readObject(input, AtomicIOClusterMessage.class);
            // 将反序列化后的 POJO 发布到 Disruptor
            disruptorManager.publish(disruptorEntry -> disruptorEntry.setClusterMessage(message));
        } catch (Exception e) {
            log.error("反序列化集群消息失败", e);
        }
    }

}
