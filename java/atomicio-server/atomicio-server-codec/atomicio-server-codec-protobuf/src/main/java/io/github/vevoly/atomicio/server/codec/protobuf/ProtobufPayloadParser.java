package io.github.vevoly.atomicio.server.codec.protobuf;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.github.vevoly.atomicio.codec.protobuf.ProtobufMessage;
import io.github.vevoly.atomicio.codec.protobuf.proto.StringRequest;
import io.github.vevoly.atomicio.protocol.api.codec.AtomicIOPayloadParser;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import org.springframework.stereotype.Component;

/**
 * Protobuf 消息解析器
 *
 * @since 0.6.7
 * @author vevoly
 */
public class ProtobufPayloadParser implements AtomicIOPayloadParser {
    @Override
    public <T> T parse(AtomicIOMessage message, Class<T> clazz) throws Exception {
        if (!(message instanceof ProtobufMessage)) {
            throw new IllegalArgumentException("This parser only supports ProtobufMessage.");
        }

        Any payload = ((ProtobufMessage) message).getAnyPayload();

        if (Message.class.isAssignableFrom(clazz)) {
            if (payload.is((Class<? extends Message>) clazz)) {
                return (T) payload.unpack((Class<? extends Message>) clazz);
            }
        }
        throw new InvalidProtocolBufferException("Payload type 不匹配或不是 Protobuf message class.");
    }

    @Override
    public String parseAsString(AtomicIOMessage message) throws Exception {
        StringRequest request = this.parse(message, StringRequest.class);
        return request.getValue();
    }
}
