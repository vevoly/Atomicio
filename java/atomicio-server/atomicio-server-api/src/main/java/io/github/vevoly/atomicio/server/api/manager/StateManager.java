package io.github.vevoly.atomicio.server.api.manager;

import io.github.vevoly.atomicio.common.api.dto.SessionDetails;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 状态管理器接口
 * 这是框架中所有【分布式】或【全局】状态的统一访问层 (Facade)。
 * 定义了对会话、群组、路由等逻辑状态的【原子操作】。
 * 实现类 (AtomicIOStateManager) 负责编排底层的 StateProvider 和 ClusterManager，以保证这些操作在分布式环境下的最终一致性。
 * StateManager 与 SessionManager (管理本地物理连接) 和 GroupManager (管理本地 ChannelGroup) 共同构成了框架的三大核心管理器。
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
     * 获取当前节点 ID
     */
    String getCurrentNodeId();

    // =====================================================================
    //  会话状态写入 (Session State Writes)
    // =====================================================================

    /**
     * 注册会话
     * @param request
     * @param isMultiLogin
     * @return
     */
//    CompletableFuture<Map<String, String>> register(AtomicIOBindRequest request, boolean isMultiLogin);

    /**
     * 【原子操作】注册一个新的会话，不执行任何替换/踢人逻辑。
     * 仅用于多端登录，或在 {@link io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine}
     * 完成冲突决策后，确认可以安全注册新会话时调用。
     * @param request 包含了 userId, deviceId, deviceType 的绑定请求
     * @return 操作完成的 Future
     */
    CompletableFuture<Void> register(AtomicIOBindRequest request);

    /**
     * 【原子操作】以原子方式替换多个会话：注销指定的旧会话，并注册一个新会话。
     * 这是实现所有“踢旧上新”策略的核心方法。
     * 此方法会触发向被踢会话所在节点的集群通知。
     *
     * @param newRequest    新登录的会话请求
     * @param devicesToKick 需要被原子性注销的旧设备ID集合
     * @return a Future of a Map，包含了被成功踢掉的设备ID到其原所在节点ID的映射
     */
    CompletableFuture<Map<String, String>> replaceAndNotify(AtomicIOBindRequest newRequest, Set<String> devicesToKick);

    /**
     * 【原子操作】注销一个指定的用户会话。
     * 通常在连接断开 (clearSession) 或用户主动登出时调用。
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @return 操作完成的 Future
     */
    CompletableFuture<Void> unregister(String userId, String deviceId);

    /**
     * 【原子操作】强制注销一个用户的所有在线会话，并触发集群踢人通知。
     * 通常由管理员操作或业务逻辑强制调用。
     *
     * @param userId 要被踢出的用户ID
     * @return a Future of a Map，包含了被成功踢掉的所有设备ID到其原所在节点ID的映射
     */
    CompletableFuture<Map<String, String>> kickUserGlobalAndNotify(String userId);

    // =====================================================================
    //  会话状态读取 (Session State Reads)
    // =====================================================================

    /**
     * 检查指定用户的特定设备是否已在全局在线。
     * 这是 {@link io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine}
     * 进行登录冲突决策前的核心【预检】方法。
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @return a Future of a boolean，表示设备是否在线
     */
    CompletableFuture<Boolean> isDeviceOnline(String userId, String deviceId);

    /**
     * 查询一个用户的所有会话的详细信息。
     *
     * @param userId 用户ID
     * @return a Future of a Map, key 是 deviceId, value 是 {@link SessionDetails}
     */
    CompletableFuture<Map<String, SessionDetails>> findSessionDetails(String userId);

    /**
     * 查询一个用户在特定设备类型上的所有会话详情。
     *
     * @param userId     用户ID
     * @param deviceType 设备类型 (e.g., "PC", "iOS")
     * @return a Future of a Map, key 是 deviceId, value 是 {@link SessionDetails}
     */
    CompletableFuture<Map<String, SessionDetails>> findSessionDetailsByType(String userId, String deviceType);

    /**
     * 查询一个用户当前所在的节点ID
     * @param userId 用户ID
     * @return a Future of the primary nodeId of the user.
     */
    CompletableFuture<Set<String>> findNodesForUser(String userId);

    /**
     * 批量查询多个用户所在的节点ID。
     * 这是一个性能优化接口，用于集群消息的精准投递。
     *
     * @param userIds 要查询的用户ID列表
     * @return a Future of a Map，key 是 userId, value 是该用户所在的主节点ID
     */
    CompletableFuture<Map<String, Set<String>>> findNodesForUsers(List<String> userIds);

    // =====================================================================
    //  群组状态管理 (Group State Management)
    // =====================================================================

    /**
     * 将一个用户加入一个群组。
     * @param groupId 群组ID
     * @param userId  用户ID
     * @return 操作完成的 Future
     */
    CompletableFuture<Void> joinGroup(String groupId, String userId);

    /**
     * 将一个用户从一个群组中移除。
     * @param groupId 群组ID
     * @param userId  用户ID
     * @return 操作完成的 Future
     */
    CompletableFuture<Void> leaveGroup(String groupId, String userId);

    /**
     * 获取一个群组的所有成员的 UserID 列表。
     * @param groupId 群组ID
     * @return a Future of a Set of user IDs
     */
    CompletableFuture<Set<String>> getGroupMembers(String groupId);

}
