package io.github.vevoly.atomicio.codec.protobuf;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.github.vevoly.atomicio.codec.protobuf.proto.GenericMessage;
import io.github.vevoly.atomicio.api.AtomicIOMessage;

/**
 * 将 Protobuf 的 GenericMessage 适配到 AtomicIOMessage 接口。
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

    // 反向转换的方法，方便编码
    public static GenericMessage toProto(AtomicIOMessage message) {
//        try {
            if (message instanceof ProtobufMessage) {
                return ((ProtobufMessage) message).protoMessage;
            }
            return GenericMessage.newBuilder()
                    .setCommandId(message.getCommandId())
//                    .setPayload(Any.parseFrom(ByteString.copyFrom(message.getPayload())))
                    .setPayload(ByteString.copyFrom(message.getPayload()))
                    .build();
//        } catch (InvalidProtocolBufferException e) {
//            throw new RuntimeException(e);
//        }
    }

}
