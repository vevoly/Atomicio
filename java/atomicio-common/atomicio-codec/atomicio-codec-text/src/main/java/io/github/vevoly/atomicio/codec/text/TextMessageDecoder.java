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

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) throws Exception {
        String text = byteBuf.toString(StandardCharsets.UTF_8);
        if (text.isEmpty()) return;

        try {
            // Format: cmdId:userId:content:deviceId

            int firstColon = text.indexOf(':');
            int lastColon = text.lastIndexOf(':');

            if (firstColon == -1 || firstColon == lastColon) {
                log.warn("Invalid format. Expected cmdId:userId:content:deviceId but got: {}", text);
                return;
            }

            int commandId = Integer.parseInt(text.substring(0, firstColon));
            String deviceId = text.substring(lastColon + 1);

            // Middle part: userId:content
            String middle = text.substring(firstColon + 1, lastColon);
            int middleColon = middle.indexOf(':');

            if (middleColon == -1) {
                log.warn("Invalid middle format. Expected userId:content but got: {}", middle);
                return;
            }

            String userId = middle.substring(0, middleColon);
            String content = middle.substring(middleColon + 1);

            // We can pass userId into the TextMessage if you add a field,
            // or just keep it in content. For now, let's keep TextMessage generic:
            TextMessage message = new TextMessage(commandId, deviceId, userId + ":" + content);
            out.add(message);

        } catch (Exception e) {
            log.warn("Decode error for: '{}'", text, e);
        }
    }
}
