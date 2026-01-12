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
     * 心跳
     */
    public static final int HEARTBEAT = 1; // 心跳

    /**
     * 强制下线通知 (S -> C)。
     */
    public static final int KICK_OUT_NOTIFY = 2;

}
