package io.github.vevoly.atomicio.client.api;

import io.github.vevoly.atomicio.client.api.listeners.OnErrorListener;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.protocol.api.result.AuthResult;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
    CompletableFuture<Void> connect();

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
     * 登录到服务器。
     * @param userId 用户 ID
     * @param token 用户 Token
     * @param deviceId 设备 ID
     * @return a CompletableFuture that will be notified when the login attempt is complete.
     */
    CompletableFuture<AuthResult> login(String userId, String token, String deviceId);

    /**
     * 登出服务器
     * @return
     */
    CompletableFuture<Void> logout();

    /**
     * 加入群组
     * @param groupId 群 ID
     * @return
     */
    CompletableFuture<Void> joinGroup(String groupId);

    /**
     * 离开群组
     * @param groupId 群 ID
     * @return
     */
    CompletableFuture<Void> leaveGroup(String groupId);


    /**
     * 向指定的一个或多个用户发送消息。
     *
     * @param message  要发送的、实现了 AtomicIOMessage 接口的消息对象。
     *                 协议无关性，使用者负责创建符合当前协议的消息 (e.g., TextMessage, ProtobufMessage)。
     * @param userIds  一个或多个目标用户的ID。
     * @return 一个 Future，表示消息已被成功写入网络通道。
     */
    CompletableFuture<Void> sendToUsers(AtomicIOMessage message, String... userIds);

    /**
     * 向指定的群组发送消息。
     * 这是框架提供的最核心的多播/广播路由能力。
     *
     * @param message        要发送的消息对象。
     * @param groupId        目标群组的ID。
     * @param excludeUserIds (可选) 需要从接收者中排除的用户ID (通常是发送者自己)。
     * @return 一个 Future，表示消息已被成功写入网络通道。
     */
    CompletableFuture<Void> sendToGroup(AtomicIOMessage message, String groupId, Set<String> excludeUserIds);

    /**
     * 向所有在线用户广播消息。
     */
    CompletableFuture<Void> broadcast(AtomicIOMessage message);


    /**
     * 注册一个连接成功时的监听器。
     */
    void onConnected(Consumer<Void> listener);

    /**
     * 注册一个连接断开时的监听器。
     */
    void onDisconnected(Consumer<Void> listener);

    /**
     * 注册一个用于接收服务器主动推送消息（如P2P、群聊）的监听器。
     * 这是处理业务消息的主要入口。
     */
    void onPushMessage(Consumer<AtomicIOMessage> listener);

    /**
     * 注册一个发生错误时的监听器。
     */
    void onError(Consumer<Throwable> listener);

    /**
     * 注册一个正在尝试重连时的监听器。
     * @param listener 一个 BiConsumer 回调，其中：
     *                 - 第一个 Integer 参数 (t) 代表：当前是第几次尝试重连 (attempt)
     *                 - 第二个 Integer 参数 (u) 代表：本次尝试将在多少秒后发起 (delaySeconds)
     */
    void onReconnecting(BiConsumer<Integer, Integer> listener);

}
