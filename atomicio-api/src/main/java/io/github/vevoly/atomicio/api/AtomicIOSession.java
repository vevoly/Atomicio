package io.github.vevoly.atomicio.api;

/**
 * 代表一个客户端与服务器之间的连接会话。
 * 它屏蔽了底层的网络细节 (如 Netty Channel)。
 *
 * @since 0.0.1
 * @author vevoly
 */
public interface AtomicIOSession {

    /**
     * 获取会话的唯一ID。
     * @return Session ID
     */
    String getId();

    /**
     * 向当前会话发送消息。
     * @param message 消息对象
     */
    void send(AtomicIOMessage message);

    /**
     * 关闭当前会话。
     */
    void close();

    /**
     * 检查会话是否仍然处于活动状态。
     * @return true 如果连接是活动的
     */
    boolean isActive();

    /**
     * 获取客户端的远程地址。
     * @return 客户端的 IP 地址和端口
     */
    String getRemoteAddress();

    /**
     * 获取会话创建的时间戳。
     * @return a long value representing the time the session was created, measured in milliseconds from the epoch.
     */
    long getCreationTime();

    /**
     * 获取最后一次读或写操作的时间戳。
     * 可用于实现更复杂的空闲检测逻辑。
     * @return a long value representing the time of the last I/O activity.
     */
    long getLastActivityTime();

    /**
     * 在会话中附加一个属性。可用于存储业务数据，如绑定的用户ID。
     * @param key   属性键
     * @param value 属性值
     */
    void setAttribute(String key, Object value);

    /**
     * 从会话中获取一个属性。
     * @param key 属性键
     * @return 属性值，如果不存在则返回 null
     */
    <T> T getAttribute(String key);
}
