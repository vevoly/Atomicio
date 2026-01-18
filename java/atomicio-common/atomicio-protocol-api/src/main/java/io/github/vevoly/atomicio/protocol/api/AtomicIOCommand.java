package io.github.vevoly.atomicio.protocol.api;

/**
 * Atomicio 框架指令
 *
 * @since 0.0.2
 * @author vevoly
 */
public class AtomicIOCommand {

    private AtomicIOCommand() {}

    /**
     * C -> S: 心跳请求
     */
    public static final int HEARTBEAT_REQUEST = 1; // 心跳

    /**
     * S -> C: 心跳响应
     */
    public static final int HEARTBEAT_RESPONSE = 2;

    /**
     * S -> C: 强制下线通知。由框架在处理多设备登录冲突时自动发出。
     */
    public static final int KICK_OUT_NOTIFY = 10;

    /**
     * C -> S: 登录/认证请求。这是业务逻辑发起绑定的信号。
     */
    public static final int LOGIN_REQUEST = 101;
    /**
     * S -> C: 登录/认证响应。
     */
    public static final int LOGIN_RESPONSE = 102;

    /**
     * C -> S: 登出请求。
     */
    public static final int LOGOUT_REQUEST = 103;

    /**
     * C -> S: 加入群组请求
     */
    public static final int JOIN_GROUP_REQUEST = 201;
    /**
     * S -> C: 加入群组响应
     */
    public static final int JOIN_GROUP_RESPONSE = 202;

    /**
     * C -> S: 离开群组请求
     */
    public static final int LEAVE_GROUP_REQUEST = 203;
    /**
     * S -> C: 离开群组响应
     */
    public static final int LEAVE_GROUP_RESPONSE = 204;
}
