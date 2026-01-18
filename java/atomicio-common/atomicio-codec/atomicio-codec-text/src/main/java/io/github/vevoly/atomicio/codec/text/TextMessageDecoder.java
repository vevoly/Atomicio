package io.github.vevoly.atomicio.codec.text;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 将 ByteBuf (单行) 解码为 TextMessage。
 * 这个解码器期望 Pipeline 中的上一个 Handler 是一个帧解码器，
 * 例如 LineBasedFrameDecoder，它传递过来的 ByteBuf 已经是一条完整的消息。
 *
 * @since 0.0.2
 * @author vevoly
 */
@Slf4j
@ChannelHandler.Sharable
public class TextMessageDecoder extends MessageToMessageDecoder<ByteBuf> {

    /**
     * 文本协议格式
     * 格式: sequenceId:commandId:deviceId:payload
     * - sequenceId: long
     * - commandId: int
     * - deviceId: String (can be empty)
     * - payload: String (everything after the 3rd colon)
     */
    private static final int METADATA_PARTS = 4;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) throws Exception {
        String text = byteBuf.toString(StandardCharsets.UTF_8);
        if (text.isEmpty()) {
            return;
        }
        String[] parts = text.split(":", METADATA_PARTS);
        if (parts.length < METADATA_PARTS) {
            log.warn("Invalid TextMessage format. Expected at least {} parts, but got {}. Raw: '{}'",
                    METADATA_PARTS, parts.length, text);
            return;
        }

        try {
            long sequenceId = Long.parseLong(parts[0]);
            int commandId = Integer.parseInt(parts[1]);
            String deviceId = parts[2];
            String content = parts[3];

            TextMessage message = new TextMessage(sequenceId, commandId, deviceId, content);
            out.add(message);

        } catch (NumberFormatException e) {
            log.warn("Failed to parse sequenceId or commandId from TextMessage. Raw: '{}'", text, e);
        } catch (Exception e) {
            log.error("An unexpected error occurred during TextMessage decoding. Raw: '{}'", text, e);
        }
    }

}
