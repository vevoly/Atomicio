package io.github.vevoly.atomicio.server.api.state;

import io.github.vevoly.atomicio.common.api.dto.SessionDetails;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
     * 注册一个新的会话，但不执行任何踢人逻辑。
     * 用于多端登录和决策后的“执行”阶段。
     */
    CompletableFuture<Void> register(AtomicIOBindRequest request, String nodeId);

    /**
     * 原子性地替换会话：踢掉指定的旧设备，并注册新设备。
     * 返回被踢掉的会话信息。
     *
     * @param newRequest    新登录的会话请求
     * @param newNodeId     新会话所在的节点ID
     * @param devicesToKick 需要被原子性踢掉的设备ID集合
     * @return a Future of a Map containing the kicked deviceId -> nodeId mapping.
     */
    CompletableFuture<Map<String, String>> replaceSession(AtomicIOBindRequest newRequest, String newNodeId, Set<String> devicesToKick);

    /**
     * 注销一个指定用户的指定设备会话
     * 当一个用户的最后一个设备下线时，这个方法需要清理 userId -> nodeId 的全局映射
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @return a Future that completes when the operation is finished.
     */
    CompletableFuture<Void> unregister(String userId, String deviceId);

    /**
     * 注销一个用户的所有会话（例如，管理员踢人时）
     * 这个方法需要清理 userId -> nodeId 的全局映射
     *
     * @param userId 用户ID
     * @return a Future of a Map containing all deviceId -> nodeId mappings of the sessions that were unregistered.
     */
    CompletableFuture<Map<String, String>> unregisterAll(String userId);

    /**
     * 查询一个用户在特定设备类型上的所有会话详情。
     * 用于实现“按设备类型单点登录”。
     *
     * @param userId         用户ID
     * @param deviceType     设备类型
     * @return a Future of a Map where key is deviceId, value is SessionDetails.
     */
    CompletableFuture<Map<String, SessionDetails>> findSessionDetailsByType(String userId, String deviceType);

    /**
     * 查询一个用户的所有会话详情。
     * 用于“全局单点登录”和“有限多点登录”的检查。
     */
    CompletableFuture<Map<String, SessionDetails>> findSessionDetails(String userId);

    /**
     * 查询一个用户在线的所有节点ID。
     * @param userId 用户ID
     * @return a Future of a Set containing all nodeIds where the user is online.
     */
    CompletableFuture<Set<String>> findNodesForUser(String userId);

    /**
     * 批量查询多个用户所在的节点
     * 这是一个性能优化，旨在减少与状态存储的交互次数。
     *
     * @param userIds 要查询的用户ID列表
     * @return a Future of a Map where key is userId and value is the primary nodeId.
     *         如果某个用户不在线，Map 中将不包含该用户的条目。
     */
    CompletableFuture<Map<String, Set<String>>> findNodesForUsers(List<String> userIds);

    /**
     * 检查一个用户是否在线（至少有一个会话）
     *
     * @param userId 用户ID
     * @return a Future of a boolean.
     */
    CompletableFuture<Boolean> isUserOnline(String userId);

    /**
     * 检查指定用户的特定设备是否已在线。
     * @param userId    用户 ID
     * @param deviceId  设备 ID
     * @return a Future of a boolean.
     */
    CompletableFuture<Boolean> isDeviceOnline(String userId, String deviceId);

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

    /**
     * 更新一个会话的最后活跃时间戳。
     * 这是一个“尽力而为”的操作，可以异步执行，不要求强一致性。
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @param activityTime 时间戳
     */
    void updateSessionActivity(String userId, String deviceId, long activityTime);
}
