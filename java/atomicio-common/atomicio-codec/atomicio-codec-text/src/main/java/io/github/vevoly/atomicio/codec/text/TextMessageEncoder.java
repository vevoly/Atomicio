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
    protected void encode(ChannelHandlerContext ctx, TextMessage msg, ByteBuf out) {
        StringBuilder sb = new StringBuilder();

        // 按照协议格式依次追加元数据和载荷
        sb.append(msg.getSequenceId());
        sb.append(':');
        sb.append(msg.getCommandId());
        sb.append(':');
        // 确保 deviceId 和 content 不为 null，避免 "null" 字符串被写入
        sb.append(msg.getDeviceId() != null ? msg.getDeviceId() : "");
        sb.append(':');
        sb.append(msg.getContent() != null ? msg.getContent() : "");
        sb.append('\n');

        out.writeBytes(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
