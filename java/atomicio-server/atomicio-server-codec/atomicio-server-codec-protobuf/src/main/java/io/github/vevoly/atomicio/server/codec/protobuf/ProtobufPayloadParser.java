package io.github.vevoly.atomicio.server.codec.protobuf;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.github.vevoly.atomicio.codec.protobuf.ProtobufMessage;
import io.github.vevoly.atomicio.codec.protobuf.proto.ForwardToGroupRequest;
import io.github.vevoly.atomicio.codec.protobuf.proto.ForwardToUserRequest;
import io.github.vevoly.atomicio.codec.protobuf.proto.ForwardToUsersRequest;
import io.github.vevoly.atomicio.codec.protobuf.proto.StringRequest;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.codec.AtomicIOPayloadParser;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.protocol.api.routing.AtomicIOForwardingEnvelope;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Override
    public AtomicIOForwardingEnvelope parseAsForwardingEnvelope(AtomicIOMessage message) throws Exception {
        // 根据 commandId 决定解析成哪种具体的 Request
        switch (message.getCommandId()) {
            case AtomicIOCommand.SEND_TO_USER:
                // a. 解析为单播请求
                ForwardToUserRequest userRequest = this.parse(message, ForwardToUserRequest.class);
                // b. 适配为通用信封
                return new ForwardToUserEnvelopeAdapter(userRequest);

            case AtomicIOCommand.SEND_TO_USERS:
                // a. 解析为多播请求
                ForwardToUsersRequest usersRequest = this.parse(message, ForwardToUsersRequest.class);
                // b. 适配为通用信封
                return new ForwardToUsersEnvelopeAdapter(usersRequest);

            case AtomicIOCommand.SEND_TO_GROUP:
                // a. 解析为群组请求
                ForwardToGroupRequest groupRequest = this.parse(message, ForwardToGroupRequest.class);
                // b. 适配为通用信封
                return new ForwardToGroupEnvelopeAdapter(groupRequest);

            default:
                throw new IllegalArgumentException("Message with commandId " + message.getCommandId() + " is not a forwardable message.");
        }
    }

    /**
     * 将具体的 ForwardToUserRequest 适配为通用的 ForwardingEnvelope 接口。
     */
    private static class ForwardToUserEnvelopeAdapter implements AtomicIOForwardingEnvelope {
        private final ForwardToUserRequest request;

        public ForwardToUserEnvelopeAdapter(ForwardToUserRequest request) { this.request = request; }

        @Override
        public List<String> getToUserIds() { return Collections.singletonList(request.getToUserId()); }
        @Override
        public String getToGroupId() { return null; }
        @Override
        public Set<String> getExcludeUserIds() { return Collections.emptySet(); }
        @Override
        public int getBusinessPayloadType() { return request.getBusinessPayloadType(); }
        @Override
        public Object getBusinessPayload() { return request.getBusinessPayload(); } // ★★★ 直接返回 ByteString ★★★
    }

    /**
     * 将具体的 ForwardToUsersRequest 适配为通用的 ForwardingEnvelope 接口。
     */
    private static class ForwardToUsersEnvelopeAdapter implements AtomicIOForwardingEnvelope {
        private final ForwardToUsersRequest request;

        public ForwardToUsersEnvelopeAdapter(ForwardToUsersRequest request) { this.request = request; }

        @Override
        public List<String> getToUserIds() { return request.getToUserIdsList(); }
        @Override
        public String getToGroupId() { return null; }
        @Override
        public Set<String> getExcludeUserIds() { return Collections.emptySet(); }
        @Override
        public int getBusinessPayloadType() { return request.getBusinessPayloadType(); }
        @Override
        public Object getBusinessPayload() { return request.getBusinessPayload(); }
    }

    /**
     * 将具体的 ForwardToGroupRequest 适配为通用的 ForwardingEnvelope 接口。
     */
    private static class ForwardToGroupEnvelopeAdapter implements AtomicIOForwardingEnvelope {
        private final ForwardToGroupRequest request;

        public ForwardToGroupEnvelopeAdapter(ForwardToGroupRequest request) { this.request = request; }

        @Override
        public List<String> getToUserIds() { return Collections.emptyList(); }
        @Override
        public String getToGroupId() { return request.getToGroupId(); }
        @Override
        public Set<String> getExcludeUserIds() { return new HashSet<>(request.getExcludeUserIdsList()); }
        @Override
        public int getBusinessPayloadType() { return request.getBusinessPayloadType(); }
        @Override
        public Object getBusinessPayload() { return request.getBusinessPayload(); }
    }
}
