package io.github.vevoly.atomicio.api;

import io.github.vevoly.atomicio.api.listeners.ErrorEventListener;
import io.github.vevoly.atomicio.api.listeners.MessageEventListener;
import io.github.vevoly.atomicio.api.listeners.SessionEventListener;

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
     * 注册 CONNECT, DISCONNECT, IDLE 事件的监听器。。
     * @param eventType 必须是 CONNECT, DISCONNECT, 或 IDLE
     * @param listener  监听器实现
     */
    void on(AtomicIOEventType eventType, SessionEventListener listener);

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
     * 将一个已认证的用户ID与一个Session进行双向绑定。
     * 这个方法应该由认证成功后的业务逻辑来调用。
     * @param userId 用户ID
     * @param session 用户的会话
     */
    void bindUser(String userId, AtomicIOSession session);

    /**
     * 向指定用户发送消息。引擎会自动寻找该用户所在的节点并投递。
     * @param userId  目标用户ID
     * @param message 消息对象
     */
    void sendToUser(String userId, AtomicIOMessage message);

    /**
     * 将一个用户加入到指定的组。
     * 组可以是任何业务概念：房间、队伍、公会、聊天频道等。
     * 如果组不存在，引擎应自动创建。
     * @param groupId 组的唯一标识符，例如 "room-123", "guild-avengers"
     * @param userId 要加入的用户ID
     */
    void joinGroup(String groupId, String userId);

    /**
     * 将一个用户从指定的组中移除。
     * @param groupId 组的唯一标识符
     * @param userId 要移除的用户ID
     */
    void leaveGroup(String groupId, String userId);

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
}
