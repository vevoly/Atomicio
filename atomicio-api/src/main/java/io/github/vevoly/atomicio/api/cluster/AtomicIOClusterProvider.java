package io.github.vevoly.atomicio.api.cluster;

import java.util.function.Consumer;

/**
 * 集群通信契约接口
 * 负责在集群节点间发布和订阅消息
 *
 * @since 0.0.4
 * @author vevoly
 */
public interface AtomicIOClusterProvider {

    /**
     * 初始化并启动
     */
    void start();

    /**
     * 关闭
     */
    void shutdown();

    /**
     * 将一条集群消息发布出去，让其他节点能够收到
     * @param message 集群消息
     */
    void publish(AtomicIOClusterMessage message);

    /**
     * 订阅集群消息
     * 当收到来自其他节点的消息时，会调用传入的 listener
     * @param listener  消息监听器
     */
    void subscribe(Consumer<AtomicIOClusterMessage> listener);
}
