package io.github.vevoly.atomicio.codec.protobuf;

import io.github.vevoly.atomicio.codec.protobuf.proto.GenericMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;

/**
 * Protobuf 消息适配器
 * 将 ProtobufMessage 转换为 GenericMessage，反之亦然。
 *
 * @since 0.3.0
 * @author vevoly
 */
@ChannelHandler.Sharable // 可以被多 Channel 共享
public class ProtobufAdapterHandler extends MessageToMessageCodec<GenericMessage, ProtobufMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ProtobufMessage msg, List<Object> out) throws Exception {
        // 出站：将 ProtobufMessage 转换回 GenericMessage
        out.add(ProtobufMessage.toProto(msg));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, GenericMessage msg, List<Object> out) throws Exception {
        // 入站：将 GenericMessage 包装成 ProtobufMessage
        out.add(new ProtobufMessage(msg));
    }
}
