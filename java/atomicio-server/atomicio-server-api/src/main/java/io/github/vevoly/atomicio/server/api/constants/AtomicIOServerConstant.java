package io.github.vevoly.atomicio.server.api.constants;

import java.util.Set;

/**
 * 服务器端常量
 *
 * @since 0.0.6
 * @author vevoly
 */
public final class AtomicIOServerConstant {

    // -- 状态管理 --
    public static final String PREFIX = "atomicio:";
    // --- 集群 Pub/Sub 频道 ---
    private static final String CLUSTER_TOPIC_PREFIX = PREFIX + "cluster:";

    // 全局广播频道
    public static final String CLUSTER_TOPIC_GLOBAL = CLUSTER_TOPIC_PREFIX + "global";

    // 节点定向消息频道
    public static String clusterTopicForNode(String nodeId) {
        return CLUSTER_TOPIC_PREFIX + "node:" + nodeId;
    }

    public static final String IO_SESSION_KEY_NAME = PREFIX + "io-session"; // Netty Channel 属性名称，用于存储 AtomicIOSession 对象
    public static final String KICK_OUT_CHANNEL_PREFIX_NAME = PREFIX + "kickout-channel:"; // 踢出通知频道名前缀 （格式：atomicio:kickout-channel:nodeId）
    public static final String SESSIONS_KEY_PREFIX = PREFIX + "sessions:"; // 哈希表（HASH）：存储用户会话
    public static final String GROUPS_KEY_PREFIX = PREFIX + "groups:";     // 集合（SET）：存储群组成员
    public static final String GROUPS_TO_USERS_KEY_PREFIX = PREFIX + "group_members:"; // SET: groupId -> Set<userId>
    public static final String USERS_TO_GROUPS_KEY_PREFIX = PREFIX + "user_groups:";   // SET: userId -> Set<groupId>
    public static final String TOTAL_USERS_KEY = PREFIX + "stats:total_users"; // SET for unique user count
    public static final String TOTAL_SESSIONS_KEY = PREFIX + "stats:total_sessions"; // COUNTER for total session count
    public static final String USER_NODES_PREFIX = PREFIX + "stats:user_nodes:"; // SET 用户到集群映射 atomicio:state:user_nodes:userA -> nodeId1,nodeId2

    // 会话状态
    // HASH: userId -> {deviceId1: nodeId1, deviceId2: nodeId2}
    public static String userSessions(String userId) { return SESSIONS_KEY_PREFIX + userId; }
    // 用户节点
    // SET: userId -> {nodeId1, nodeId2, ...}
    public static String userNodes(String userId) { return USER_NODES_PREFIX + userId; }
    // 群组状态
    // SET: groupId -> {userId1, userId2}
    public static String groupMembers(String groupId) { return GROUPS_TO_USERS_KEY_PREFIX + groupId; }
    // SET: userId -> {groupId1, groupId2} (反向索引)
    public static String userGroups(String userId) { return USERS_TO_GROUPS_KEY_PREFIX + userId; }


    public static final String ENGINE_THREAD_NAME = "atomicio-start-thread";

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
