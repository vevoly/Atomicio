package io.github.vevoly.atomicio.server.extension.redis.state;

import io.github.vevoly.atomicio.common.api.constants.AtomicIOConstant;
import io.github.vevoly.atomicio.server.api.constants.AtomicIOServerConstant;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;
import io.github.vevoly.atomicio.server.api.state.AtomicIOGroupStateProvider;
import io.github.vevoly.atomicio.server.api.state.AtomicIOSessionStateProvider;
import io.github.vevoly.atomicio.server.api.state.AtomicIOStateProvider;
import io.github.vevoly.atomicio.server.extension.redis.lua.LuaScripts;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 基于 Redis 实现的状态提供器
 * 专为集群环境设计，提供集中化且可扩展的方式来管理会话（Session）和群组（Group）状态。
 * 所有操作均以异步方式执行。
 *
 * @since 0.6.4
 * @author vevoly
 */
@Slf4j
public class RedisStateProvider implements AtomicIOStateProvider, AtomicIOSessionStateProvider, AtomicIOGroupStateProvider {

    private RedisClient redisClient;

    private StatefulRedisConnection<String, String> connection;
    private RedisAsyncCommands<String, String> asyncCommands;

    private static final int SSCAN_THRESHOLD = 5000;            // 阈值，当群人数数量超过该值时，使用 SSCAN 命令 todo: 优化可配置
    private static final int SSCAN_COUNT_PER_ITERATION = 1000;  // 每次 SSCAN 命令获取的元素数量

    public RedisStateProvider(RedisClient redisClient) {
        if (redisClient == null) {
            throw new IllegalArgumentException("Redis Client 不能为空。");
        }
        this.redisClient = redisClient;
    }

    /**
     * 初始化 Redis 客户端，建立连接并验证连接有效性。
     */
    @Override
    public void start() {
        log.info("正在启动 RedisStateProvider ... ");
        try {
            this.connection = redisClient.connect();
            this.asyncCommands = connection.async();

            // 验证连接
            log.debug("正在验证 Redis 连接...");
            String pong = connection.sync().ping();
            if (!AtomicIOConstant.DEFAULT_HEARTBEAT_RESPONSE.equalsIgnoreCase(pong)) {
                throw new IllegalStateException("Redis PING 命令失败，收到响应: " + pong);
            }
            log.info("RedisStateProvider 已启动，连接验证成功。");
        } catch (RedisException e) {
            log.error("无法连接到 Redis 。请检查 Redis 服务器状态及配置。", e);
            throw new IllegalStateException("无法启动 RedisStateProvider: 连接 Redis 失败。", e);
        }
    }

    /**
     * 关闭 Redis 客户端并释放所有连接资源。
     */
    @Override
    public void shutdown() {
        log.info("正在关闭 RedisStateProvider...");
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    // =====================================================================
    //  StateProvider 接口实现
    // =====================================================================

    @Override
    public AtomicIOSessionStateProvider getSessionStateProvider() {
        return this;
    }

    @Override
    public AtomicIOGroupStateProvider getGroupStateProvider() {
        return this;
    }

    // =====================================================================
    //  SessionStateProvider (会话状态) 接口实现
    // =====================================================================

    @Override
    public CompletableFuture<Map<String, String>> register(AtomicIOBindRequest request, String nodeId, boolean isMultiLogin) {
        String userId = request.getUserId();
        String deviceId = request.getDeviceId();
        String userSessionsKey = AtomicIOServerConstant.userSessions(userId);

        if (isMultiLogin) {
            // 多端登录：直接 HSET，返回空 Map
            return asyncCommands.<Void>eval(
                    LuaScripts.REGISTER_MULTI_LOGIN,
                    ScriptOutputType.STATUS,
                    new String[]{userSessionsKey, AtomicIOServerConstant.USER_NODES_KEY},
                    deviceId,
                    nodeId,
                    userId
            ).thenApply(v -> Collections.<String, String>emptyMap())
                    .toCompletableFuture();
        } else {
            // 单点登录：执行 Lua 脚本
            // 参数说明：脚本内容, 返回类型, 键数组, 参数数组
            return asyncCommands.<List<String>>eval(
                    LuaScripts.REGISTER_SINGLE_LOGIN,
                    ScriptOutputType.MULTI,
                    new String[]{userSessionsKey, AtomicIOServerConstant.USER_NODES_KEY},
                    deviceId,
                    nodeId,
                    userId
            ).thenApply(this::convertListToMap) // 将 Lua 返回的 List 转为 Map<deviceId, nodeId>
            .toCompletableFuture();
        }
    }

    @Override
    public CompletableFuture<Void> unregister(String userId, String deviceId) {
        String userSessionsKey = AtomicIOServerConstant.userSessions(userId);
        return asyncCommands.<Long>eval(
                LuaScripts.UNREGISTER_SESSION,
                ScriptOutputType.INTEGER,
                new String[]{userSessionsKey, AtomicIOServerConstant.USER_NODES_KEY, AtomicIOServerConstant.TOTAL_USERS_KEY},
                deviceId, userId
        ).thenAccept(removed -> {
            if (removed > 0) {
                log.debug("Redis Session 移除成功: user={}, device={}", userId, deviceId);
            }
        }).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Map<String, String>> unregisterAll(String userId) {
        String userSessionsKey = AtomicIOServerConstant.userSessions(userId);
        return asyncCommands.<List<String>>eval(
                LuaScripts.UNREGISTER_ALL_SESSIONS,
                ScriptOutputType.MULTI,
                new String[]{userSessionsKey, AtomicIOServerConstant.USER_NODES_KEY, AtomicIOServerConstant.TOTAL_USERS_KEY},
                userId
        ).thenApply(this::convertListToMap).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Map<String, String>> findSessions(String userId) {
        String userSessionsKey = AtomicIOServerConstant.userSessions(userId);
        return asyncCommands.hgetall(userSessionsKey).toCompletableFuture();
    }

    @Override
    public CompletableFuture<String> findNodeForUser(String userId) {
        return asyncCommands.hget(AtomicIOServerConstant.USER_NODES_KEY, userId).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Map<String, String>> findNodesForUsers(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }
        // 使用 HMGET 进行批量查询
        return asyncCommands.hmget(AtomicIOServerConstant.USER_NODES_KEY, userIds.toArray(new String[0]))
                .toCompletableFuture()
                .thenApply(keyValues -> {
                    Map<String, String> userNodeMap = new HashMap<>();
                    for (int i = 0; i < userIds.size(); i++) {
                        KeyValue<String, String> kv = keyValues.get(i);
                        // Lettuce 的 hmget 返回的 list 中，不存在的 key 对应的 value 是空的 KeyValue
                        if (kv != null && kv.hasValue()) {
                            userNodeMap.put(userIds.get(i), kv.getValue());
                        }
                    }
                    return userNodeMap;
                });
    }

    @Override
    public CompletableFuture<Boolean> isUserOnline(String userId) {
        return asyncCommands.hexists(AtomicIOServerConstant.USER_NODES_KEY, userId).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Long> getTotalUserCount() {
        return asyncCommands.scard(AtomicIOServerConstant.TOTAL_USERS_KEY)
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<Long> getTotalSessionCount() {
        return asyncCommands.get(AtomicIOServerConstant.TOTAL_SESSIONS_KEY)
                .thenApply(val -> val != null ? Long.parseLong(val) : 0L)
                .toCompletableFuture();
    }

    // =====================================================================
    //  GroupStateProvider (群组状态) 接口实现
    // =====================================================================

    @Override
    public CompletableFuture<Void> join(String groupId, String userId) {
        return asyncCommands.<Void>eval(
                LuaScripts.JOIN_GROUP,
                ScriptOutputType.STATUS,
                new String[]{AtomicIOServerConstant.groupMembers(groupId), AtomicIOServerConstant.userGroups(userId)},
                userId, groupId
        ).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Void> leave(String groupId, String userId) {
        return asyncCommands.<Void>eval(
                LuaScripts.LEAVE_GROUP,
                ScriptOutputType.STATUS,
                new String[]{AtomicIOServerConstant.groupMembers(groupId), AtomicIOServerConstant.userGroups(userId)},
                userId, groupId
        ).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Set<String>> getGroupMembers(String groupId) {
        String key = AtomicIOServerConstant.GROUPS_KEY_PREFIX + groupId;

        // 1. 先用 SCARD 获取总数
        return asyncCommands.scard(key).thenCompose(size -> {
            // 2. 根据总数进行决策
            if (size == null || size == 0) {
                return CompletableFuture.completedFuture(Collections.emptySet());
            }

            if (size <= SSCAN_THRESHOLD) {
                // 策略 A: 小群，使用 SMEMBERS
                return asyncCommands.smembers(key).toCompletableFuture();
            } else {
                // 策略 B: 大群，使用 SSCAN
                return sscanAll(key);
            }
        }).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Long> getGroupMemberCount(String groupId) {
        String key = AtomicIOServerConstant.GROUPS_KEY_PREFIX + groupId;
        return asyncCommands.scard(key).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Boolean> isGroupMember(String groupId, String userId) {
        String key = AtomicIOServerConstant.GROUPS_KEY_PREFIX + groupId;
        return asyncCommands.sismember(key, userId).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Set<String>> getGroupsForUser(String userId) {
        String key = AtomicIOServerConstant.GROUPS_TO_USERS_KEY_PREFIX + userId;
        return asyncCommands.scard(key).thenCompose(size -> {
            if (size == null || size == 0) {
                return CompletableFuture.completedFuture(Collections.<String>emptySet());
            }
            if (size <= SSCAN_THRESHOLD) {
                return asyncCommands.smembers(key).toCompletableFuture();
            } else {
                return sscanAll(key);
            }
        }).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Long> getGroupCountForUser(String userId) {
        String key = AtomicIOServerConstant.USERS_TO_GROUPS_KEY_PREFIX + userId;
        return asyncCommands.scard(key).toCompletableFuture();
    }

    /**
     * 使用 SSCAN 迭代获取一个 Set 的所有成员。
     * 这是一个非常通用的、处理大 Set 的模式。
     */
    private CompletableFuture<Set<String>> sscanAll(String key) {
        CompletableFuture<Set<String>> finalFuture = new CompletableFuture<>();
        Set<String> allMembers = new HashSet<>();
        // 递归地进行 SSCAN
        sscanRecursive(key, ScanCursor.INITIAL, allMembers, finalFuture);
        return finalFuture;
    }

    private void sscanRecursive(String key, ScanCursor cursor, Set<String> allMembers, CompletableFuture<Set<String>> finalFuture) {
        ScanArgs scanArgs = ScanArgs.Builder.limit(SSCAN_COUNT_PER_ITERATION);
        asyncCommands.sscan(key, cursor, scanArgs)
                .whenComplete((cursorWithResult, throwable) -> {
                    if (throwable != null) {
                        log.error("Error during SSCAN for key {}", key, throwable);
                        finalFuture.completeExceptionally(throwable);
                        return;
                    }
                    // 将本次迭代的结果添加到总集合中
                    allMembers.addAll(cursorWithResult.getValues());
                    // 检查迭代是否完成
                    if (cursorWithResult.isFinished()) {
                        // 完成
                        finalFuture.complete(allMembers);
                    } else {
                        // 未完成，使用新的 cursor 进行下一次递归调用
                        sscanRecursive(key, cursorWithResult, allMembers, finalFuture);
                    }
                });
    }

    /**
     * 将 Redis 返回的 [key1, value1, key2, value2...] 列表转换为 Map
     */
    private Map<String, String> convertListToMap(List<String> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < list.size(); i += 2) {
            map.put(list.get(i), list.get(i + 1));
        }
        return map;
    }

}
