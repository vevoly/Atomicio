package io.github.vevoly.atomicio.server.extension.redis.state;

import io.github.vevoly.atomicio.common.api.constants.AtomicIOConstant;
import io.github.vevoly.atomicio.common.api.dto.SessionDetails;
import io.github.vevoly.atomicio.server.api.constants.AtomicIOServerConstant;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;
import io.github.vevoly.atomicio.server.api.state.AtomicIOGroupStateProvider;
import io.github.vevoly.atomicio.server.api.state.AtomicIOSessionStateProvider;
import io.github.vevoly.atomicio.server.api.state.AtomicIOStateProvider;
import io.github.vevoly.atomicio.server.extension.redis.lua.LuaScripts;
import io.github.vevoly.atomicio.server.extension.redis.utils.JsonUtils;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
    public CompletableFuture<Void> register(AtomicIOBindRequest request, String nodeId) {
        String userId = request.getUserId();
        String deviceId = request.getDeviceId();

        SessionDetails details = new SessionDetails(nodeId, request.getDeviceType(), System.currentTimeMillis(), System.currentTimeMillis());
        String detailsJson = JsonUtils.serialize(details);

        return asyncCommands.<Void>eval(
                LuaScripts.REGISTER_SESSION,
                ScriptOutputType.STATUS,
                // KEYS[1]: userSessionsKey
                // KEYS[2]: userNodesKey
                // KEYS[3]: totalUsersKey
                new String[]{AtomicIOServerConstant.userSessions(userId), AtomicIOServerConstant.userNodes(userId), AtomicIOServerConstant.TOTAL_USERS_KEY},
                deviceId, detailsJson, userId, nodeId
        ).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Map<String, String>> replaceSession(AtomicIOBindRequest newRequest, String newNodeId, Set<String> devicesToKick) {
        String userId = newRequest.getUserId();

        SessionDetails newDetails = new SessionDetails(newNodeId, newRequest.getDeviceType(), System.currentTimeMillis(), System.currentTimeMillis());
        String newDetailsJson = JsonUtils.serialize(newDetails);

        // 构建 Lua 脚本的 ARGV
        List<String> argv = new ArrayList<>();
        argv.add(newRequest.getDeviceId());
        argv.add(newDetailsJson);
        argv.add(userId);
        argv.add(newNodeId);
        argv.addAll(devicesToKick);

        return asyncCommands.<List<String>>eval(
                        LuaScripts.REPLACE_SESSIONS,
                        ScriptOutputType.MULTI,
                        new String[]{AtomicIOServerConstant.userSessions(userId), AtomicIOServerConstant.userNodes(userId), AtomicIOServerConstant.TOTAL_USERS_KEY},
                        argv.toArray(new String[0])
                ).thenApply(JsonUtils::convertListToJsonMap) // 这里返回的 value 是 JSON
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<Void> unregister(String userId, String deviceId) {
        String userSessionsKey = AtomicIOServerConstant.userSessions(userId);
        return asyncCommands.<Long>eval(
                LuaScripts.UNREGISTER_SESSION,
                ScriptOutputType.INTEGER,
                new String[]{userSessionsKey, AtomicIOServerConstant.userNodes(userId), AtomicIOServerConstant.TOTAL_USERS_KEY},
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
                new String[]{userSessionsKey, AtomicIOServerConstant.userNodes(userId), AtomicIOServerConstant.TOTAL_USERS_KEY},
                userId
        ).thenApply(JsonUtils::convertListToJsonMap).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Map<String, SessionDetails>> findSessionDetailsByType(String userId, String deviceType) {
        if (deviceType == null) return CompletableFuture.completedFuture(Collections.emptyMap());
        return asyncCommands.<List<String>>eval(
                LuaScripts.FIND_SESSION_DETAILS_BY_TYPE,
                ScriptOutputType.MULTI,
                new String[]{AtomicIOServerConstant.userSessions(userId)},
                deviceType
        ).thenApply(JsonUtils::convertListToDetailsMap).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Map<String, SessionDetails>> findSessionDetails(String userId) {
        return asyncCommands.hgetall(AtomicIOServerConstant.userSessions(userId))
                .thenApply(JsonUtils::convertMapToDetailsMap)
                .toCompletableFuture();
    }


    @Override
    public CompletableFuture<Set<String>> findNodesForUser(String userId) {
        return asyncCommands.smembers(AtomicIOServerConstant.userNodes(userId)).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Map<String, Set<String>>> findNodesForUsers(List<String> userIds) {
        // 使用 Redis Pipeline 来批量执行 SMEMBERS
        List<CompletableFuture<Set<String>>> futures = userIds.stream()
                .map(this::findNodesForUser)
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<String, Set<String>> resultMap = new HashMap<>();
                    for (int i = 0; i < userIds.size(); i++) {
                        resultMap.put(userIds.get(i), futures.get(i).join());
                    }
                    return resultMap;
                });
    }

    @Override
    public CompletableFuture<Boolean> isUserOnline(String userId) {
        return asyncCommands.exists(AtomicIOServerConstant.userNodes(userId))
                .thenApply(count -> count != null && count > 0)
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<Boolean> isDeviceOnline(String userId, String deviceId) {
        if (userId == null || deviceId == null) {
            return CompletableFuture.completedFuture(false);
        }
        // 1. 获取该用户会话的 HASH Key
        String userSessionsKey = AtomicIOServerConstant.userSessions(userId);
        // 2. 使用 Redis 的 HEXISTS 命令
        // 作用：检查名为 userSessionsKey 的 HASH 中，是否存在名为 deviceId 的字段。这是一个 O(1) 操作，极其高效。
        log.debug("集群检查: HEXISTS {} {}", userSessionsKey, deviceId);
        return asyncCommands.hexists(userSessionsKey, deviceId).toCompletableFuture();
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

    @Override
    public void updateSessionActivity(String userId, String deviceId, long activityTime) {
        String userSessionsKey = AtomicIOServerConstant.userSessions(userId);
        asyncCommands.hget(userSessionsKey, deviceId).thenAccept(detailsJson -> {
            if (detailsJson != null) {
                // 反序列化，更新时间戳，再序列化
                SessionDetails details = JsonUtils.deserialize(detailsJson, SessionDetails.class);
                details.setLastActivityTime(activityTime);
                String newDetailsJson = JsonUtils.serialize(details);
                // 写回 Redis
                // 注意：这里可能存在并发更新的竞争条件，但对于活跃时间戳，
                // todo 少量覆盖是可以接受的。更严格的实现可以使用 Lua 或 WATCH。
                asyncCommands.hset(userSessionsKey, deviceId, newDetailsJson);
            }
        });
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
