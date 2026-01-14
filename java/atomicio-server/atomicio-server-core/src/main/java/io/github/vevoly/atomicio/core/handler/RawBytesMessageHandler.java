package io.github.vevoly.atomicio.core.handler;

import io.github.vevoly.atomicio.core.message.RawBytesMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * 原始字节消息处理器
 * “绿色通道”处理器，专门用于处理 RawBytesMessage
 * 它必须被放在所有常规协议编码器之前
 *
 * @since 0.6.3
 * @author vevoly
 */
@ChannelHandler.Sharable
public class RawBytesMessageHandler extends MessageToMessageEncoder<RawBytesMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, RawBytesMessage msg, List<Object> out) throws Exception {
        byte[] payload = msg.getPayload();
        if (payload != null && payload.length > 0) {
            // 直接将内部的、已经编码好的字节，包装成 ByteBuf 并传递下去
            out.add(Unpooled.wrappedBuffer(payload));
        }
    }
}
