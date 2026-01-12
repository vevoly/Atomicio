package io.github.vevoly.atomicio.codec.protobuf;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.github.vevoly.atomicio.codec.protobuf.proto.GenericMessage;
import io.github.vevoly.atomicio.protocol.api.AtomicIOMessage;

/**
 * 将 Protobuf 的 GenericMessage 适配到 AtomicIOMessage 接口。
 *
 * @since 0.4.2
 * @author vevoly
 */
public class ProtobufMessage implements AtomicIOMessage {

    private final GenericMessage protoMessage;

    public ProtobufMessage(GenericMessage protoMessage) {
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
    public static AtomicIOMessage of(int commandId, Message message) {
        // 1. 将业务消息序列化为字节
        byte[] payload = message.toByteArray();
        // 2. 创建我们的通用信封 GenericMessage
        GenericMessage genericMessage = null;
        try {
            genericMessage = GenericMessage.newBuilder()
                    .setCommandId(commandId)
                    .setPayload(Any.parseFrom(ByteString.copyFrom(payload)))
                    .build();
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        // 3. 将信封包装成 ProtobufMessage (我们的 AtomicIOMessage 适配器)
        //    编码器在发送时，会处理这个 ProtobufMessage，将其转换为 GenericMessage 的字节流
        return new ProtobufMessage(genericMessage);
    }
}
