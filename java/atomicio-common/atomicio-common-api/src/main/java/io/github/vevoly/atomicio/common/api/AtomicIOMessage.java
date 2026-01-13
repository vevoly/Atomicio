package io.github.vevoly.atomicio.common.api;

/**
 * 消息的顶层接口。
 * 上层应用应创建自己的消息类来实现此接口。
 *
 * @since 0.0.1
 * @author vevoly
 */
public interface AtomicIOMessage {

    /**
     * 获取消息的指令ID或类型。
     * 引擎可以根据此ID进行路由或插件化处理。
     * @return 指令ID
     */
    int getCommandId();

    /**
     * 获取消息体（Payload）。
     * 返回 byte[]，以支持各种序列化方式 (Protobuf, JSON, etc.)。
     * @return 消息体字节数组
     */
    byte[] getPayload();
}
