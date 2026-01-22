package io.github.vevoly.atomicio.example.protobuf.common.cmd;

/**
 * 业务指令
 *
 * @since 0.6.7
 * @author vevoly
 */
public class BusinessCommand {

    // --- 框架内置命令 ---
    /**
     * 请查阅 {@link io.github.vevoly.atomicio.protocol.api.AtomicIOCommand}
     */

    // --- 业务指令 ---

    /**
     * 【业务】C -> S: 发送一条点对点消息的请求。
     */
    public static final int P2P_MESSAGE_REQUEST = 2001;

    /**
     * 【业务】S -> C: 服务器向接收者推送一条新的点对点消息。
     */
    public static final int P2P_MESSAGE_NOTIFY = 2002;

    /**
     * 【业务】S -> C: 服务器向发送者确认消息已收到/处理。
     */
    public static final int P2P_MESSAGE_ACK = 2003;


    /**
     * 【业务】C -> S: 发送一条群消息的请求。
     */
    public static final int GROUP_MESSAGE_REQUEST = 3005;

    /**
     * 【业务】S -> C: 服务器向群内成员推送一条新的群消息。
     */
    public static final int GROUP_MESSAGE_NOTIFY = 3006; // 群消息通知

    /**
     * 【业务】S-> C: 服务器向发送者确认群消息已收到/处理。
     */
    public static final int GROUP_MESSAGE_ACK = 3007; // 群消息到达确认

}
