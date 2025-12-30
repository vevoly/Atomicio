package io.github.vevoly.atomicio.api;

/**
 * 引擎可触发的事件类型
 * 对应网络事件的完整生命周期：
 * 1. Connect:    生命周期开始
 * 2. Message:    生命中的交互
 * 3. Idle:       静默状态
 * 4. Error:      生命中的意外
 * 5. Disconnect: 生命周期结束
 *
 *
 * @since 0.0.1
 * @author vevoly
 */
public enum AtomicIOEventType {

    /**
     * 当一个新连接建立并准备好时触发。
     */
    CONNECT,

    /**
     * 当一个连接断开时触发。
     */
    DISCONNECT,

    /**
     * 当收到一个完整的消息时触发。
     */
    MESSAGE,

    /**
     * 当连接出现异常时触发。
     */
    ERROR,

    /**
     * 当连接空闲超时时触发（心跳）。
     */
    IDLE
}
