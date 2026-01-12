package io.github.vevoly.atomicio.client.api;

import io.github.vevoly.atomicio.protocol.api.AtomicIOMessage;
import io.github.vevoly.atomicio.client.api.listeners.*;

import java.util.concurrent.Future;

/**
 * Atomicio 客户端 SDK 的主接口。
 *
 * @since 0.5.0
 * @author vevoly
 */
public interface AtomicIOClient {

    /**
     * 异步连接到服务器。
     * 如果启用了自动重连，此方法在断开后会自动尝试重连。
     *
     * @return a Future that will be notified when the initial connection attempt is complete.
     */
    Future<Void> connect();

    /**
     * 主动断开与服务器的连接。
     * 如果启用了自动重连，调用此方法后将不会再进行重连。
     */
    void disconnect();

    /**
     * 检查客户端当前是否处于已连接状态。
     * @return true if the channel is active, false otherwise.
     */
    boolean isConnected();

    /**
     * 异步发送消息到服务器。
     *
     * @param message 要发送的消息对象
     * @return a Future representing the result of the send operation.
     */
    Future<Void> send(AtomicIOMessage message);

    // --- 事件注册 (链式调用) ---

    /**
     * 注册一个连接成功时的监听器。
     */
    AtomicIOClient onConnected(OnConnectedListener listener);

    /**
     * 注册一个连接断开时的监听器。
     */
    AtomicIOClient onDisconnected(OnDisconnectedListener listener);

    /**
     * 注册一个收到服务器消息时的监听器。
     */
    AtomicIOClient onMessage(OnMessageListener listener);

    /**
     * 注册一个发生错误时的监听器。
     */
    AtomicIOClient onError(OnErrorListener listener);

    /**
     * 注册一个正在尝试重连时的监听器。
     */
    AtomicIOClient onReconnecting(OnReconnectingListener listener);
}
