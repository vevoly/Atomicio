package io.github.vevoly.atomicio.server.extension.redis.lua;

import io.github.vevoly.atomicio.server.api.constants.AtomicIOServerConstant;

/**
 * Lua 脚本
 *
 * @since 0.6.4
 * @@author vevoly
 */
public final class LuaScripts {

    private LuaScripts() {}

    /**
     * 注册会话（多端登录模式）。
     * 原子性地更新 sessions hash 和 user_nodes hash。
     * KEYS[1]: userSessionsKey (e.g., atomicio:sessions:userId)
     * KEYS[2]: userNodesKey (e.g., atomicio:user_nodes)
     * KEYS[3]: total_user (e.g., atomicio:stats:total_users)
     * ARGV[1]: deviceId
     * ARGV[2]: nodeId
     * ARGV[3]: userId
     */
    @Deprecated
    public static final String REGISTER_MULTI_LOGIN =
            "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                    "redis.call('hset', KEYS[2], ARGV[3], ARGV[2]); " +
                    "redis.call('sadd', KEYS[3], ARGV[3]); " +
                    "redis.call('incr', '" + AtomicIOServerConstant.TOTAL_SESSIONS_KEY + "');";

    /**
     * 注册会话（单点登录模式）。
     * 原子性地替换 session，更新 user_nodes，并返回被踢掉的旧 session。
     * KEYS & ARGV 同上。
     */
    @Deprecated
    public static final String REGISTER_SINGLE_LOGIN =
            "local kicked = redis.call('hgetall', KEYS[1]); " +
                    "local old_count = #kicked / 2; " +
                    "redis.call('decrby', '" + AtomicIOServerConstant.TOTAL_SESSIONS_KEY + "', old_count); " +
                    "redis.call('del', KEYS[1]); " +
                    "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                    "redis.call('hset', KEYS[2], ARGV[3], ARGV[2]); " +
                    "redis.call('sadd', KEYS[3], ARGV[3]); " +
                    "redis.call('incr', '" + AtomicIOServerConstant.TOTAL_SESSIONS_KEY + "'); " +
                    "return kicked;";

    /**
     * 注册一个新会话。
     * 原子性地更新 sessions hash 和 user_nodes set。
     * KEYS[1]: userSessionsKey
     * KEYS[2]: userNodesKey (e.g., atomicio:user_nodes:userId)
     * KEYS[3]: totalUsersKey
     * ARGV[1]: deviceId
     * ARGV[2]: sessionDetailsJson
     * ARGV[3]: userId
     * ARGV[4]: nodeId
     */
    public static final String REGISTER_SESSION =
            "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +        // 添加或更新设备详情
                    "redis.call('sadd', KEYS[2], ARGV[4]); " +         // 将 nodeId 添加到用户的节点集合中
                    "redis.call('sadd', KEYS[3], ARGV[3]); " +         // 将 userId 添加到总用户集合
                    "redis.call('incr', '" + AtomicIOServerConstant.TOTAL_SESSIONS_KEY + "');";

    /**
     * 原子性地替换会话。
     * 1. 踢掉指定的旧设备。
     * 2. 注册新设备。
     * 3. 检查并清理不再使用的 nodeId。
     * KEYS[1]: userSessionsKey
     * KEYS[2]: userNodesKey
     * KEYS[3]: totalUsersKey
     * ARGV[1]: newDeviceId
     * ARGV[2]: newSessionDetailsJson
     * ARGV[3]: newUserId
     * ARGV[4]: newNodeId
     * ARGV[5...]: deviceIdsToKick
     * @return 返回被踢掉的 session 详情: [deviceId1, detailsJson1, ...]
     */
    public static final String REPLACE_SESSIONS =
            "local kicked_sessions = {}; " +
                    "local kick_count = 0; " +
                    "local old_node_ids = {}; " + // 记录被踢设备的 nodeId
                    // 1. 遍历并删除要踢的设备
                    "for i = 5, #ARGV do " +
                    "local device_to_kick = ARGV[i]; " +
                    "local kicked_details_json = redis.call('hget', KEYS[1], device_to_kick); " +
                    "if kicked_details_json then " +
                    "if redis.call('hdel', KEYS[1], device_to_kick) > 0 then " +
                    "kick_count = kick_count + 1; " +
                    "table.insert(kicked_sessions, device_to_kick); " +
                    "table.insert(kicked_sessions, kicked_details_json); " +
                    "local kicked_details = cjson.decode(kicked_details_json); " +
                    "old_node_ids[kicked_details.nodeId] = (old_node_ids[kicked_details.nodeId] or 0) + 1; " +
                    "end; " +
                    "end; " +
                    "end; " +
                    // 2. 添加新设备
                    "local is_new = redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                    "if is_new == 1 then redis.call('incr', '" + AtomicIOServerConstant.TOTAL_SESSIONS_KEY + "'); end; " +
                    "redis.call('decrby', '" + AtomicIOServerConstant.TOTAL_SESSIONS_KEY + "', kick_count); " +
                    "redis.call('sadd', KEYS[2], ARGV[4]); " +
                    "redis.call('sadd', KEYS[3], ARGV[3]); " +
                    // 3. 检查并清理不再使用的 nodeId
                    "local current_sessions = redis.call('hvals', KEYS[1]); " +
                    "for old_node_id, count in pairs(old_node_ids) do " +
                    "local still_in_use = false; " +
                    "for _, session_details_json in ipairs(current_sessions) do " +
                    "local session_details = cjson.decode(session_details_json); " +
                    "if session_details.nodeId == old_node_id then " +
                    "still_in_use = true; break; " +
                    "end; " +
                    "end; " +
                    "if not still_in_use then " +
                    "redis.call('srem', KEYS[2], old_node_id); " +
                    "end; " +
                    "end; " +

                    "return kicked_sessions;";

    /**
     * 注销单个会话。
     * KEYS[1]: userSessionsKey
     * KEYS[2]: userNodesKey
     * KEYS[3]: totalUsersKey
     * ARGV[1]: deviceId
     * ARGV[2]: userId
     */
    public static final String UNREGISTER_SESSION =
            "local details_json = redis.call('hget', KEYS[1], ARGV[1]); " +
                    "if not details_json then return 0; end; " +
                    "local removed = redis.call('hdel', KEYS[1], ARGV[1]); " +
                    "if removed > 0 then " +
                    "redis.call('decr', '" + AtomicIOServerConstant.TOTAL_SESSIONS_KEY + "'); " +
                    "local details = cjson.decode(details_json); " +
                    "local remaining_on_node = 0; " +
                    "local current_sessions = redis.call('hvals', KEYS[1]); " +
                    "for _, session_details_json in ipairs(current_sessions) do " +
                    "local session_details = cjson.decode(session_details_json); " +
                    "if session_details.nodeId == details.nodeId then " +
                    "remaining_on_node = remaining_on_node + 1; " +
                    "end; " +
                    "end; " +
                    "if remaining_on_node == 0 then " +
                    "redis.call('srem', KEYS[2], details.nodeId); " + // ★ 清理 user_nodes set
                    "end; " +
                    "if #current_sessions == 0 then " +
                    "redis.call('srem', KEYS[3], ARGV[2]); " +
                    "redis.call('del', KEYS[2]); " + // 如果用户所有设备都下线了，也删除 user_nodes key
                    "end; " +
                    "end; " +
                    "return removed;";

    /**
     * 注销用户的所有会话。
     * KEYS[1]: userSessionsKey
     * KEYS[2]: userNodesKey
     * KEYS[3]: totalUsersKey
     * ARGV[1]: userId
     * @return 返回被删除的旧会话详情列表 [deviceId1, detailsJson1, ...]
     */
    public static final String UNREGISTER_ALL_SESSIONS =
            "local old_sessions = redis.call('hgetall', KEYS[1]); " +
                    "if #old_sessions > 0 then " +
                    "local session_count = #old_sessions / 2; " +
                    "redis.call('decrby', '" + AtomicIOServerConstant.TOTAL_SESSIONS_KEY + "', session_count); " +
                    "redis.call('del', KEYS[1]); " +
                    "redis.call('del', KEYS[2]); " +   // 直接删除整个 user_nodes set
                    "redis.call('srem', KEYS[3], ARGV[1]); " +
                    "end; " +
                    "return old_sessions;";

    /**
     * 按设备类型过滤会话详情。
     * KEYS[1]: userSessionsKey
     * ARGV[1]: deviceType
     * @return 返回过滤后的会话详情列表 (deviceId -> SessionDetails)
     */
    public static final String FIND_SESSION_DETAILS_BY_TYPE =
            "local sessions = redis.call('hgetall', KEYS[1]); " +
                    "local result = {}; " +
                    "for i = 1, #sessions, 2 do " +
                    "local details = cjson.decode(sessions[i+1]); " +
                    "if details.deviceType == ARGV[1] then " +
                    "table.insert(result, sessions[i]); " +
                    "table.insert(result, sessions[i+1]); " +
                    "end; " +
                    "end; " +
                    "return result;";
    /**
     * 加入群组。
     * 原子性地更新群成员列表和用户的群组列表（反向索引）。
     * KEYS[1]: groupMembersKey
     * KEYS[2]: userGroupsKey
     * ARGV[1]: userId
     * ARGV[2]: groupId
     */
    public static final String JOIN_GROUP =
            "redis.call('sadd', KEYS[1], ARGV[1]); " +         // sadd groupMembersKey userId
                    "redis.call('sadd', KEYS[2], ARGV[2]);";   // sadd userGroupsKey groupId

    /**
     * 离开群组。
     * 原子性地更新群成员列表和用户的群组列表（反向索引）。
     * KEYS & ARGV 同上。
     */
    public static final String LEAVE_GROUP =
            "redis.call('srem', KEYS[1], ARGV[1]); " +         // srem groupMembersKey userId
                    "redis.call('srem', KEYS[2], ARGV[2]);";   // srem userGroupsKey groupId
}
