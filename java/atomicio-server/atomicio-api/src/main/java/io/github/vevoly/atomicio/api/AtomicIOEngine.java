package io.github.vevoly.atomicio.api;

import io.github.vevoly.atomicio.api.listeners.*;
import io.github.vevoly.atomicio.api.session.AtomicIOBindRequest;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.concurrent.Future;

/**
 * AtomicIO 引擎的顶层接口。
 *
 * @since 0.0.1
 * @author vevoly
 */
public interface AtomicIOEngine {

    /**
     * 启动引擎。这是一个异步操作。
     * @return 一个 Future，可用于判断启动是否完成或失败。
     */
    Future<Void> start();

    /**
     * 优雅地关闭引擎。
     */
    void shutdown();

    /**
     * 在 READY 引擎准备就绪时事件监听器
     * @param listener
     */
    void onReady(EngineReadyListener listener);

    /**
     * 注册 CONNECT 事件的监听器。
     * @param listener
     */
    void onConnect(ConnectEventListener listener);

    /**
     * 注册 DISCONNECT 事件的监听器。
     * @param listener
     */
    void onDisconnect(DisconnectEventListener listener);

    /**
     * 注册 MESSAGE 事件的监听器。
     * @param listener 消息事件监听器
     */
    void onMessage(MessageEventListener listener);

    /**
     * 注册 ERROR 事件的监听器。
     * @param listener 异常事件监听器
     */
    void onError(ErrorEventListener listener);

    /**
     * 注册 IDLE 事件的监听器。
     * @param listener 空闲事件监听器
     * @param listener
     */
    void onIdle(IdleEventListener listener);

    /**
     * 会话被  事件的监听器
     * @param listener
     */
    void onSessionReplaced(SessionReplacedListener listener);

    /**
     * 将一个已认证的用户ID与一个Session进行双向绑定。
     * 这个方法应该由认证成功后的业务逻辑来调用。
     * @param request 绑定请求
     * @param session 用户的会话
     */
    void bindUser(AtomicIOBindRequest request, AtomicIOSession session);

    /**
     * 向指定用户发送消息。引擎会自动寻找该用户所在的节点并投递。
     * @param userId  目标用户ID
     * @param message 消息对象
     */
    void sendToUser(String userId, AtomicIOMessage message);

    /**
     * 将用户加入到指定的组（用户的所有会话）。
     * 组可以是任何业务概念：房间、队伍、公会、聊天频道等。
     * 如果组不存在，引擎应自动创建。
     * @param groupId 组的唯一标识符，例如 "room-123", "guild-avengers"
     * @param userId 要加入的用户ID
     */
    void joinGroup(String groupId, String userId);

    /**
     * 用户的其中一个会话加入群组
     * 多端模式下精确控制
     * @param groupId   群组 id
     * @param session   会话
     */
    void joinGroup(String groupId, AtomicIOSession session);

    /**
     * 将一个用户从指定的组中移除。
     * 指定用户所有在线会话都离开一个组
     * @param groupId 组的唯一标识符
     * @param userId 要移除的用户ID
     */
    void leaveGroup(String groupId, String userId);

    /**
     * 让一个指定的会话离开一个组。
     * 多端模式下精确控制
     * @param groupId 组ID
     * @param session 要离开的会话
     */
    void leaveGroup(String groupId, AtomicIOSession session);

    /**
     * 向一个组内的所有成员（除了可选的排除者）发送消息。
     * 这是游戏服务器中最常用的方法！
     * @param groupId 目标组的ID
     * @param message 消息对象
     * @param excludeUserIds 可选的、需要排除的用户ID列表（例如，不把移动消息发给自己）
     */
    void sendToGroup(String groupId, AtomicIOMessage message, String... excludeUserIds);

    /**
     * 向所有在线用户广播消息。
     * @param message 消息对象
     */
    void broadcast(AtomicIOMessage message);

    /**
     * 主动踢掉一个用户的所有在线会话。
     * @param userId 要踢掉的用户ID
     * @param kickOutMessage 一个可选的、在关闭连接前要发送的通知消息。如果为 null，则不发送通知。
     * @return a List of the sessions that were kicked.
     */
    List<AtomicIOSession> kickUser(String userId, @Nullable AtomicIOMessage kickOutMessage);
}
