package io.github.vevoly.atomicio.codec;

import io.github.vevoly.atomicio.api.codec.AtomicIOCodecProvider;
import io.github.vevoly.atomicio.codec.protobuf.ProtobufAdapterHandler;
import io.github.vevoly.atomicio.codec.protobuf.proto.GenericMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * Protobuf 协议的 CodecProvider 实现。
 *
 * @since 0.3.0
 * @author vevoly
 */
public class ProtobufCodecProvider implements AtomicIOCodecProvider {

    @Override
    public ChannelHandler getEncoder() {
        // Netty 官方 Protobuf 编码器
        return new ProtobufEncoder();
    }

    @Override
    public ChannelHandler getDecoder() {
        // Netty 官方 Protobuf 解码器
        return new ProtobufDecoder(GenericMessage.getDefaultInstance());
    }

    @Override
    public ChannelHandler getFrameDecoder() {
        // Protobuf 官方推荐的、基于 Varint32 的帧解码器
        // 自动处理 TCP 粘包/半包问题
        return new ProtobufVarint32FrameDecoder();
    }

    @Override
    public void buildPipeline(ChannelPipeline pipeline) {
        // Outbound
        pipeline.addLast(getLengthFieldPrepender());
        pipeline.addLast(getEncoder());


        // Inbound
        pipeline.addLast("A_Logger", new LoggingHandler(LogLevel.INFO)); // 窃听点 A: 原始数据流
        pipeline.addLast(getFrameDecoder());
        pipeline.addLast("B_Logger", new LoggingHandler(LogLevel.INFO)); // 窃听点 B: 检查分帧结果
        pipeline.addLast(getDecoder());

        // 适配器
        pipeline.addLast("C_Logger", new LoggingHandler(LogLevel.INFO)); // 窃听点 C: 检查 Protobuf 解码结果
        pipeline.addLast(new ProtobufAdapterHandler());
        pipeline.addLast("D_Logger", new LoggingHandler(LogLevel.INFO)); // 窃听点 D: 检查适配器转换结果
    }

    // **我们还需要一个 Prepender (前置器) 来配合 FrameDecoder**
    // 我们可以把它也看作是编码器的一部分
    public ChannelHandler getLengthFieldPrepender() {
        // 这个 Handler 在编码时，会自动在消息包前面加上一个 Varint32 的长度字段
        return new ProtobufVarint32LengthFieldPrepender();
    }

}