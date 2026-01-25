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
     * 注销单个会话。
     * 如果这是用户的最后一个会话，则原子性地清理所有相关数据。
     * KEYS[1]: userSessionsKey
     * KEYS[2]: userNodesKey
     * KEYS[3]: totalUsersKey
     * ARGV[1]: deviceId
     * ARGV[2]: userId
     */
    public static final String UNREGISTER_SESSION =
            "local removed = redis.call('hdel', KEYS[1], ARGV[1]); " +
                    "if removed > 0 then " +
                    "redis.call('decr', '" + AtomicIOServerConstant.TOTAL_SESSIONS_KEY + "'); " +
                    "local remaining = redis.call('hlen', KEYS[1]); " +
                    "if remaining == 0 then " +
                    "redis.call('hdel', KEYS[2], ARGV[2]); " +
                    "redis.call('srem', KEYS[3], ARGV[2]); " +
                    "end; " +
                    "end; " +
                    "return removed;";

    /**
     * 注销用户的所有会话。
     * 原子性地清理该用户的所有会话数据和全局映射。
     * KEYS: userSessionsKey
     * KEYS: userNodesKey
     * KEYS: totalUsersKey
     * ARGV: userId
     * @return 返回被删除的旧会话列表 (deviceId -> nodeId)
     */
    public static final String UNREGISTER_ALL_SESSIONS =
            "local old_sessions = redis.call('hgetall', KEYS[1]); " +
                    "if #old_sessions > 0 then " +
                    "local session_count = #old_sessions / 2; " +
                    "redis.call('decrby', '" + AtomicIOServerConstant.TOTAL_SESSIONS_KEY + "', session_count); " +
                    "redis.call('del', KEYS[1]); " +
                    "redis.call('hdel', KEYS[2], ARGV[1]); " + // ★ 使用 ARGV[1] = userId
                    "redis.call('srem', KEYS[3], ARGV[1]); " + // ★ 使用 ARGV[1] = userId
                    "end; " +
                    "return old_sessions;";

    /**
     * 加入群组。
     * 原子性地更新群成员列表和用户的群组列表（反向索引）。
     * KEYS: groupMembersKey
     * KEYS: userGroupsKey
     * ARGV: userId
     * ARGV: groupId
     */
    public static final String JOIN_GROUP =
            "redis.call('sadd', KEYS, ARGV); " +
                    "redis.call('sadd', KEYS, ARGV);";

    /**
     * 离开群组。
     * 原子性地更新群成员列表和用户的群组列表（反向索引）。
     * KEYS & ARGV 同上。
     */
    public static final String LEAVE_GROUP =
            "redis.call('srem', KEYS, ARGV); " +
                    "redis.call('srem', KEYS, ARGV);";
}
