package io.github.vevoly.atomicio.server.api.cluster;

import java.util.function.Consumer;

/**
 * 集群通信契约提供器接口
 * 负责在集群节点间发布和订阅消息
 *
 * @since 0.0.4
 * @author vevoly
 */
public interface AtomicIOClusterProvider {

    /**
     * 获取当前节点 ID
     */
    String getCurrentNodeId();

    /**
     * 初始化并启动
     */
    void start();

    /**
     * 关闭
     */
    void shutdown();

    /**
     * 发布二进制集群数据
     * @param data 二进制集群数据
     */
    void publish(byte[] data);

    /**
     * 发布二进制踢人数据
     * @param nodeId
     * @param data
     */
    void publishKickOut(String nodeId, byte[] data);

    /**
     * 订阅二进制集群数据
     * @param dataConsumer  二进制集群数据消费者
     */
    void subscribe(Consumer<byte[]> dataConsumer);

    /**
     * 订阅被踢出事件
     * @param kickOutConsumer 踢出逻辑的回调处理，接收的消息通常为 "userId:deviceId"
     */
    void subscribeKickOut(Consumer<byte[]> kickOutConsumer);
}
