package io.github.vevoly.atomicio.protocol.api.message;

import lombok.Getter;

/**
 * AtomicIOMessage 的抽象基类，处理通用字段如 sequenceId。
 * 所有具体的消息实现都继承此类。
 *
 * @since 0.6.5
 * @author vevoly
 */
@Getter
public abstract class AbstractAtomicIOMessage implements AtomicIOMessage {

    /**
     * 消息的序列号。
     * 客户端发起
     */
    protected final long sequenceId;

    /**
     * 构造函数，强制子类在创建时必须提供 sequenceId。
     * @param sequenceId 消息的序列号
     */
    protected AbstractAtomicIOMessage(long sequenceId) {
        this.sequenceId = sequenceId;
    }


    @Override
    public abstract int getCommandId();

    @Override
    public abstract byte[] getPayload();
}
