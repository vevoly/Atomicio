package io.github.vevoly.atomicio.example.simple.cmd;

/**
 * 业务指令
 */
public class BusinessCommand {

    /**
     * 发送帮助/欢迎信息
     */
    public static final int SYSTEM_WELCOME_NOTIFY = 1000;

    public static final int P2P_MESSAGE = 2001;      // 点对点消息
    public static final int GROUP_MESSAGE = 3001;    // 群消息

    // 框架指令请查阅 AtomicIOCommand

}
