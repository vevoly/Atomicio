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
    void publish(String channel, byte[] data);

    /**
     * 订阅二进制集群数据
     * @param dataConsumer  二进制集群数据消费者
     * @param channels      订阅的频道
     */
    void subscribe(Consumer<byte[]> dataConsumer, String... channels);

}
