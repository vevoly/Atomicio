package io.github.vevoly.atomicio.core.message;

import io.github.vevoly.atomicio.protocol.api.message.AbstractAtomicIOMessage;

/**
 * 原始字节消息
 * 内部消息实现，用于封装一个已经是最终编码形态的字节数组。
 * 当 Pipeline 中的 {@link io.github.vevoly.atomicio.core.handler.RawBytesMessageHandler}
 * 拦截到这个类型的消息时，它会直接将内部的 payload 写入网络，绕过所有后续的常规协议编码器。
 *
 * @since 0.6.3
 * @author vevoly
 */
public class RawBytesMessage extends AbstractAtomicIOMessage {

    private final byte[] finalPayload;

    public RawBytesMessage(byte[] finalPayload) {
        super(0);
        this.finalPayload = finalPayload;
    }

    /**
     * 对于一个已经被预编码的消息，commandId 的概念已经不那么重要了，
     * 因为它已经被编码进了 payload。但为了遵守接口，我们可以返回一个特殊值。
     */
    @Override
    public int getCommandId() {
        // 返回一个特殊值，表示这是一个预编码的转发消息
        return -22;
    }

    @Override
    public byte[] getPayload() {
        return this.finalPayload;
    }
}
