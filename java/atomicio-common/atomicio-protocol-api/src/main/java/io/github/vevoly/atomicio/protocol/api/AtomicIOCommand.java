package io.github.vevoly.atomicio.protocol.api;

/**
 * Atomicio 框架指令
 * -1：错误通知
 * 1-99: 核心系统指令 (心跳)
 * 100-199: 认证与会话指令 (登录、登出)
 * 200-299: 分组/订阅指令 (加/离群组)
 * 300-499: (空置) 为未来可能出现的新类型的框架能力（比如RPC调用、文件传输信令）预留空间。
 * 500-599: 数据路由指令 (SEND_TO_USERS, SEND_TO_GROUP)
 *
 * @since 0.0.2
 * @author vevoly
 */
public class AtomicIOCommand {

    private AtomicIOCommand() {}

    /**
     * S -> C: 系统错误通知
     */
    public static final int SYSTEM_ERROR_NOTIFY = -1;

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

    /**
     * C -> S: 请求服务器将消息转发给一个用户
     */
    public static final int SEND_TO_USER = 501;

    /**
     * C -> S: 请求服务器将消息转发给多个指定用户
     */
    public static final int SEND_TO_USERS = 502;

    /**
     * C -> S: 请求服务器将消息转发给指定群组
     * S -> C: 服务器向客户端推送一条群组消息 (复用)
     */
    public static final int SEND_TO_GROUP = 503;

    /**
     * S -> C: 服务器向客户端推送一条点对点消息
     */
    public static final int PUSH_MESSAGE = 505;
}
