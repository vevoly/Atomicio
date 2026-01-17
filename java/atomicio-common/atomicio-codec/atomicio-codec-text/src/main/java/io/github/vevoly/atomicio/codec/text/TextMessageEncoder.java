package io.github.vevoly.atomicio.codec.text;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;

import java.nio.charset.StandardCharsets;

/**
 * 将 TextMessage 编码为 ByteBuf。
 * 协议格式: "commandId:content\n" (以换行符分隔)
 *
 * @since 0.0.2
 * @author vevoly
 */
@ChannelHandler.Sharable
public class TextMessageEncoder extends MessageToByteEncoder<TextMessage> {

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, TextMessage msg, ByteBuf out) throws Exception {
        String encoded = String.format("%d:%s:%s\n",
                msg.getCommandId(),
                msg.getDeviceId(),
                msg.getContent());
        out.writeBytes(encoded.getBytes(StandardCharsets.UTF_8));
    }
}
