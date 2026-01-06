package io.github.vevoly.atomicio.api;

/**
 * Atomicio 协议指令定义
 *
 * @since 0.0.2
 * @author vevoly
 */
public class AtomicIOCommand {

    private AtomicIOCommand() {}

    // --- 系统级指令 ---
    public static final int HEARTBEAT = 1;

    // --- 业务指令 ---
    public static final int LOGIN = 1001;
    public static final int LOGIN_RESPONSE = 1002;

    public static final int P2P_MESSAGE = 2001;
    public static final int P2P_MESSAGE_NOTIFY = 2002;
    public static final int P2P_MESSAGE_ACK = 2003;

    public static final int JOIN_GROUP = 3001;
    public static final int GROUP_MESSAGE = 3003;
}
