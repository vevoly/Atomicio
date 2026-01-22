package io.github.vevoly.atomicio.server.codec.text;

import io.github.vevoly.atomicio.protocol.api.codec.AtomicIOPayloadParser;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;

import java.nio.charset.StandardCharsets;

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
}
