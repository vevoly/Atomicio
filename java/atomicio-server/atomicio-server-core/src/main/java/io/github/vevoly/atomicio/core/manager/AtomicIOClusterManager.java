package io.github.vevoly.atomicio.core.manager;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessage;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterMessageType;
import io.github.vevoly.atomicio.server.api.cluster.AtomicIOClusterProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.io.ByteArrayOutputStream;

/**
 * 集群管理器
 *
 * @since 0.5.9
 * @author vevoly
 */
@Slf4j
public class AtomicIOClusterManager {

    private final AtomicIOClusterProvider provider;
    private final DisruptorManager disruptorManager;
    private final Kryo kryo;

    public AtomicIOClusterManager(@Nullable AtomicIOClusterProvider provider, DisruptorManager disruptorManager) {
        this.provider = provider;
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
    public void start() {
        if (provider != null) {
            provider.start();
            // 订阅原始字节，然后在这里反序列化和分发
            provider.subscribe(this::handleReceivedData);
        }
    }

    public void shutdown() {
        if (provider != null) {
            provider.shutdown();
        }
    }

    /**
     * 接收来自 Engine 的 POJO，序列化后发布
     * 替代 provider.publish
     * @param message
     */
    public void publish(AtomicIOClusterMessage message) {
        if (provider == null) {
            return;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        kryo.writeObject(output, message);
        output.close();
        // 调用底层 provider 发送原始字节
        provider.publish(baos.toByteArray());
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
            log.error("Failed to deserialize cluster message", e);
        }
    }

}
