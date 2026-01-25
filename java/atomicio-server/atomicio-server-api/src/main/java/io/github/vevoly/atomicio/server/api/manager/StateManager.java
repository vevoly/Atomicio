package io.github.vevoly.atomicio.server.api.manager;

import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 状态管理器接口
 *
 * @since 0.6.4
 * @author vevoly
 */
public interface StateManager {

    /**
     * 启动状态管理器
     */
    void start();

    /**
     * 关闭状态管理器
     */
    void shutdown();

    /**
     * 注册会话
     * @param request
     * @param isMultiLogin
     * @return
     */
    CompletableFuture<Map<String, String>> register(AtomicIOBindRequest request, boolean isMultiLogin);

    /**
     * 注销会话
     * @param userId
     * @param deviceId
     * @return
     */
    CompletableFuture<Void> unregister(String userId, String deviceId);

    /**
     * 踢出会话
     * @param userId
     * @return
     */
    CompletableFuture<Map<String, String>> kickUserGlobal(String userId);

    /**
     * 加入群组
     * @param groupId
     * @param userId
     * @return
     */
    CompletableFuture<Void> joinGroup(String groupId, String userId);

    /**
     * 离开群组
     * @param groupId
     * @param userId
     * @return
     */
    CompletableFuture<Void> leaveGroup(String groupId, String userId);

    /**
     * 获取群组成员
     * @param groupId
     * @return
     */
    CompletableFuture<Set<String>> getGroupMembers(String groupId);

    /**
     * 查询一个用户当前所在的节点ID
     * @param userId 用户ID
     * @return a Future of the primary nodeId of the user.
     */
    CompletableFuture<String> findNodeForUser(String userId);

    /**
     * 批量查询多个用户所在的节点
     * 这是一个性能优化，旨在减少与状态存储的交互次数。
     *
     * @param userIds 要查询的用户ID列表
     * @return a Future of a Map where key is userId and value is the primary nodeId.
     *         如果某个用户不在线，Map 中将不包含该用户的条目。
     */
    CompletableFuture<Map<String, String>> findNodesForUsers(List<String> userIds);
}
