package io.github.vevoly.atomicio.codec.text;

import io.netty.buffer.ByteBuf;
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
public class TextMessageDecoder extends MessageToMessageDecoder<ByteBuf> {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) throws Exception {
        // 将 LineBasedFrameDecoder 传来的单行 ByteBuf 转换为字符串
        String text = byteBuf.toString(StandardCharsets.UTF_8);
        if (text.isEmpty()) {
            return;
        }
        try {
            int colonIndex = text.indexOf(':');
            if (colonIndex == -1) {
                log.warn("Invalid message format received from {}: {}", ctx.channel().remoteAddress(), text);
                return;
            }

            int commandId = Integer.parseInt(text.substring(0, colonIndex));
            String content = text.substring(colonIndex + 1);

            TextMessage message = new TextMessage(commandId, content);
            // 将解码后的消息对象放入 out 列表，它会被传递给 Pipeline 中的下一个 Handler (EngineChannelHandler)
            out.add(message);

        } catch (NumberFormatException e) {
            log.warn("Invalid commandId format in line: '{}' from {}", text, ctx.channel().remoteAddress(), e);
        }

    }
}
