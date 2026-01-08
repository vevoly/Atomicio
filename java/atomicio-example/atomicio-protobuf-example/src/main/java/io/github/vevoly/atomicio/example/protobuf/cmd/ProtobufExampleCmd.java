package io.github.vevoly.atomicio.example.protobuf.cmd;

public class ProtobufExampleCmd {

    // --- 业务指令 ---
    public static final int LOGIN = 1001;  // 登录
    public static final int LOGIN_RESPONSE = 1002; // 登录响应

    public static final int P2P_MESSAGE = 2001; // 点对地消息
    public static final int P2P_MESSAGE_NOTIFY = 2002; // 消息通知
    public static final int P2P_MESSAGE_ACK = 2003; // 消息到达确认

    public static final int JOIN_GROUP = 3001; // 加入群组
    public static final int JOIN_GROUP_RESPONSE = 3002; // 加入群组响应

    public static final int LEAVE_GROUP = 3003; // 离开群组
    public static final int LEAVE_GROUP_RESPONSE = 3004; // 离开群组响应
    public static final int GROUP_MESSAGE = 3005; // 群消息
    public static final int GROUP_MESSAGE_NOTIFY = 3006; // 群消息通知
    public static final int GROUP_MESSAGE_ACK = 3007; // 群消息到达确认
    public static final int KICK_OUT = 3011; // 踢出群组
}
