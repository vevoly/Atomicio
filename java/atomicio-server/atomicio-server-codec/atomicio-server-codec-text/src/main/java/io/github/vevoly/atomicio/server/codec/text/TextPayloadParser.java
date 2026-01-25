package io.github.vevoly.atomicio.server.codec.text;

import io.github.vevoly.atomicio.codec.text.TextMessage;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.codec.AtomicIOPayloadParser;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.protocol.api.routing.AtomicIOForwardingEnvelope;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Text payload parser
 *
 * @since 0.6.7
 * @author vevoly
 */
public class TextPayloadParser implements AtomicIOPayloadParser {
    @Override
    public <T> T parse(AtomicIOMessage message, Class<T> clazz) throws Exception {
        return (T) message;
    }

    @Override
    public String parseAsString(AtomicIOMessage message) throws Exception {
        return new String(message.getPayload(), StandardCharsets.UTF_8);
    }

    @Override
    public AtomicIOForwardingEnvelope parseAsForwardingEnvelope(AtomicIOMessage message) throws Exception {
        if (!(message instanceof TextMessage)) {
            throw new IllegalArgumentException("TextPayloadParser only supports TextMessage.");
        }

        String payload = ((TextMessage) message).getContent();

        // ★★★ 核心修正：根据 commandId 选择不同的解析策略 ★★★
        switch (message.getCommandId()) {
            case AtomicIOCommand.SEND_TO_USER:
                return parseToSendToUser(payload);

            case AtomicIOCommand.SEND_TO_USERS:
                return parseToSendToUsersBatch(payload);

            case AtomicIOCommand.SEND_TO_GROUP:
                return parseToSendToGroup(payload);

            default:
                throw new IllegalArgumentException("Message with commandId " + message.getCommandId() + " cannot be parsed as a ForwardingEnvelope.");
        }
    }

    private AtomicIOForwardingEnvelope parseToSendToUser(String payload) {
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) throw new IllegalArgumentException("Invalid SEND_TO_USER format.");

        return new AtomicIOForwardingEnvelope() {
            public List<String> getToUserIds() { return Collections.singletonList(parts[0]); }
            public String getToGroupId() { return null; }
            public Set<String> getExcludeUserIds() { return Collections.emptySet(); }
            public int getBusinessPayloadType() { return Integer.parseInt(parts[1]); }
            public Object getBusinessPayload() { return parts[2]; }
        };
    }

    private AtomicIOForwardingEnvelope parseToSendToUsersBatch(String payload) {
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) throw new IllegalArgumentException("Invalid SEND_TO_USERS_BATCH format.");

        return new AtomicIOForwardingEnvelope() {
            public List<String> getToUserIds() { return Arrays.asList(parts[0].split(",")); }
            public String getToGroupId() { return null; }
            public Set<String> getExcludeUserIds() { return Collections.emptySet(); }
            public int getBusinessPayloadType() { return Integer.parseInt(parts[1]); }
            public Object getBusinessPayload() { return parts[2]; }
        };
    }

    private AtomicIOForwardingEnvelope parseToSendToGroup(String payload) {
        String[] parts = payload.split("\\|", 4);
        if (parts.length != 4) throw new IllegalArgumentException("Invalid SEND_TO_GROUP format.");

        return new AtomicIOForwardingEnvelope() {
            public List<String> getToUserIds() { return Collections.emptyList(); }
            public String getToGroupId() { return parts[0]; }
            public Set<String> getExcludeUserIds() {
                if (parts[1].isEmpty()) return Collections.emptySet();
                return new HashSet<>(Arrays.asList(parts[1].split(",")));
            }
            public int getBusinessPayloadType() { return Integer.parseInt(parts[2]); }
            public Object getBusinessPayload() { return parts[3]; }
        };
    }

}
