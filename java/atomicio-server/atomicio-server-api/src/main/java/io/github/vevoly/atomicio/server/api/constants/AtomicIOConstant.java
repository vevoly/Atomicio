package io.github.vevoly.atomicio.server.api.constants;

/**
 * 默认配置常量
 *
 * @since 0.0.6
 * @author vevoly
 */
public class AtomicIOConstant {

    // -- 状态管理 --
    public static final String CLUSTER_CHANNEL_NAME = "atomicio:cluster-channel"; // 集群通信 Channel 名称
    public static final String IO_SESSION_KEY_NAME = "atomicio:io-session"; // Netty Channel 属性名称，用于存储 AtomicIOSession 对象
    public static final String KICK_OUT_CHANNEL_PREFIX_NAME = "atomicio:kickout-channel:"; // 踢出通知频道名前缀 （格式：atomicio:kickout:nodeId）
    public static final String SESSIONS_KEY_PREFIX = "atomicio:sessions:"; // 哈希表（HASH）：存储用户会话
    public static final String GROUPS_KEY_PREFIX = "atomicio:groups:";     // 集合（SET）：存储群组成员
    public static final String GROUPS_TO_USERS_KEY_PREFIX = "atomicio:group_members:"; // SET: groupId -> Set<userId>
    public static final String USERS_TO_GROUPS_KEY_PREFIX = "atomicio:user_groups:";   // SET: userId -> Set<groupId>
    public static final String TOTAL_USERS_KEY = "atomicio:stats:total_users"; // SET for unique user count
    public static final String TOTAL_SESSIONS_KEY = "atomicio:stats:total_sessions"; // COUNTER for total session count


    // -- Netty Pipeline 名称 --
    public static final String PIPELINE_NAME_IP_CONNECTION_LIMIT_HANDLER = "ipConnectionLimitHandler";
    public static final String PIPELINE_NAME_SSL_HANDLER = "sslHandler";
    public static final String PIPELINE_NAME_SSL_EXCEPTION_HANDLER = "sslExceptionHandler";
    public static final String PIPELINE_NAME_ENCODER = "encoder";
    public static final String PIPELINE_NAME_DECODER = "decoder";
    public static final String PIPELINE_NAME_FRAME_DECODER = "frameDecoder";
    public static final String PIPELINE_NAME_IDLE_STATE_HANDLER = "idleStateHandler";
    public static final String PIPELINE_NAME_NETTY_EVENT_TRANSLATION_HANDLER = "nettyEventTranslationHandler";


}
