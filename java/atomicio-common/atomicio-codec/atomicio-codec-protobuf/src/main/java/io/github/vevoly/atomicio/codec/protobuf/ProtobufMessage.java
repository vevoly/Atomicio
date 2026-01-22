package io.github.vevoly.atomicio.codec.protobuf;

import com.google.protobuf.*;
import io.github.vevoly.atomicio.codec.protobuf.proto.GenericMessage;
import io.github.vevoly.atomicio.protocol.api.message.AbstractAtomicIOMessage;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;

/**
 * 将 Protobuf 的 GenericMessage 适配到 AtomicIOMessage 接口。
 *
 * @since 0.4.2
 * @author vevoly
 */
public class ProtobufMessage extends AbstractAtomicIOMessage {

    private final GenericMessage protoMessage;

    public ProtobufMessage(GenericMessage protoMessage) {
        super(protoMessage.getSequenceId());
        this.protoMessage = protoMessage;
    }

    @Override
    public int getCommandId() {
        return protoMessage.getCommandId();
    }

    @Override
    public byte[] getPayload() {
        return protoMessage.getPayload().toByteArray();
    }

    public Any getAnyPayload() {
        return this.protoMessage.getPayload();
    }

    /**
     * 反向转换的方法，方便编码
     * @param message
     * @return
     */
    public static GenericMessage toProto(AtomicIOMessage message) {
        try {
            if (message instanceof ProtobufMessage) {
                return ((ProtobufMessage) message).protoMessage;
            }
            return GenericMessage.newBuilder()
                    .setCommandId(message.getCommandId())
                    .setPayload(Any.parseFrom(ByteString.copyFrom(message.getPayload())))
                    .build();
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 根据指令ID和用户自定义的业务 Protobuf 消息，创建一个可供引擎发送的 AtomicIOMessage。
     * 这个方法是核心，它在内部完成了业务消息到通用“信封”的打包过程。
     *
     * @param commandId       消息的指令ID，应定义在 AtomicIOCommand 类中。
     * @param message         用户自己用 .proto 文件定义的、实现了 com.google.protobuf.Message 接口的业务消息对象。
     * @return 一个实现了 AtomicIOMessage 接口的实例，可以直接通过 session.send() 或 engine.sendToUser() 发送。
     */
    public static AtomicIOMessage of(long sequenceId, int commandId, Message message) {
        if (message == null) {
            message = Empty.getDefaultInstance();
        }

        // 打包业务消息
        Any anyPayload = Any.pack(message);
        GenericMessage genericMessage = GenericMessage.newBuilder()
                .setSequenceId(sequenceId)
                .setCommandId(commandId)
                .setPayload(anyPayload)
                .build();
        return new ProtobufMessage(genericMessage);
    }
}
