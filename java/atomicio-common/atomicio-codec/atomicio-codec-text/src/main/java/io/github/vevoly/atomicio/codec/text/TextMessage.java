package io.github.vevoly.atomicio.codec.text;

import io.github.vevoly.atomicio.protocol.api.AtomicIOMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.nio.charset.StandardCharsets;

/**
 * 一个简单的基于文本的 AtomicIOMessage 实现，用于测试和简单场景。
 *
 * @since 0.0.2
 * @author vevoly
 */
@ToString
@AllArgsConstructor
public class TextMessage implements AtomicIOMessage {

    private final int commandId;

    @Getter
    private final String content;


    @Override
    public int getCommandId() {
        return commandId;
    }

    @Override
    public byte[] getPayload() {
        // payload 就是字符串的 UTF-8 字节
        return content != null ? content.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }

}
