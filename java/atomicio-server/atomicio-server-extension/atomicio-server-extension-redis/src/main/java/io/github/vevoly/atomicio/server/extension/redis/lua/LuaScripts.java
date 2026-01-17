package io.github.vevoly.atomicio.server.extension.redis.lua;

/**
 * Lua 脚本
 *
 * @since 0.6.4
 * @@author vevoly
 */
public final class LuaScripts {

    /**
     * 单点登录切换脚本
     * KEYS[1]: session key (atomicio:sessions:userId)
     * ARGV[1]: new deviceId
     * ARGV[2]: new nodeId
     * 返回值: 旧的 session 数据（List形式：k1, v1, k2, v2...）
     */
    public static final String SWITCH_SINGLE_LOGIN =
            "local old = redis.call('HGETALL', KEYS[1]); " +
                    "redis.call('DEL', KEYS[1]); " +
                    "redis.call('HSET', KEYS[1], ARGV[1], ARGV[2]); " +
                    "return old;";

}
