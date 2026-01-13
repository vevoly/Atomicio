package io.github.vevoly.atomicio.codec.text;

import io.netty.buffer.ByteBuf;
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
public class TextMessageEncoder extends MessageToByteEncoder<TextMessage> {

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, TextMessage textMessage, ByteBuf out) throws Exception {
        out.writeBytes(String.valueOf(textMessage.getCommandId()).getBytes(StandardCharsets.UTF_8));
        out.writeBytes(":".getBytes(StandardCharsets.UTF_8));
        out.writeBytes(textMessage.getContent().getBytes(CharsetUtil.UTF_8));
        out.writeBytes("\n".getBytes(StandardCharsets.UTF_8));
    }
}
