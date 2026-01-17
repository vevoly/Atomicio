package io.github.vevoly.atomicio.server.api.state;

import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 会话状态提供器
 * 定义所有与“用户-会话”状态相关的读写操作
 * 所有方法都是异步的，返回 CompletableFuture。
 * 实现类负责与具体的后端存储（内存、Redis 等）进行交互。
 *
 * @since 0.6.4
 * @author vevoly
 *
 */
public interface AtomicIOSessionStateProvider {

    /**
     * 注册一个新的会话，并根据登录策略处理会话冲突
     *
     * @param request           包含 userId, deviceId 等信息的绑定请求
     * @param nodeId            当前会话所在的节点ID
     * @param isMultiLogin      是否允许多端登录
     * @return a Future of a Map. 如果是单点登录且发生了“踢人”，Map 中会包含被踢掉的会话信息 (deviceId -> nodeId)。
     *         在其他情况下，返回一个空的 Map。
     */
    CompletableFuture<Map<String, String>> register(AtomicIOBindRequest request, String nodeId, boolean isMultiLogin);

    /**
     * 注销一个指定用户的指定设备会话
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @return a Future that completes when the operation is finished.
     */
    CompletableFuture<Void> unregister(String userId, String deviceId);

    /**
     * 注销一个用户的所有会话（例如，管理员踢人时）
     *
     * @param userId 用户ID
     * @return a Future of a Map containing all deviceId -> nodeId mappings of the sessions that were unregistered.
     */
    CompletableFuture<Map<String, String>> unregisterAll(String userId);

    /**
     * 查询一个用户的所有在线会话信息
     *
     * @param userId 用户ID
     * @return a Future of a Map where key is deviceId and value is nodeId.
     */
    CompletableFuture<Map<String, String>> findSessions(String userId);

    /**
     * 检查一个用户是否在线（至少有一个会话）
     *
     * @param userId 用户ID
     * @return a Future of a boolean.
     */
    CompletableFuture<Boolean> isUserOnline(String userId);

    /**
     * 获取整个集群的总在线用户数
     * 注意：这个值是用户数，不是连接数。一个多端登录的用户只算一个
     *
     * @return a Future of a long representing the total number of unique online users.
     */
    CompletableFuture<Long> getTotalUserCount();

    /**
     * 获取整个集群的总在线连接数（会话数）
     *
     * @return a Future of a long representing the total number of active sessions.
     */
    CompletableFuture<Long> getTotalSessionCount();
}
