package io.github.vevoly.atomicio.server.codec.protobuf;

import com.google.protobuf.InvalidProtocolBufferException;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.AtomicIOMessage;
import io.github.vevoly.atomicio.codec.decoder.ProtobufVarint32FrameDecoder;
import io.github.vevoly.atomicio.codec.protobuf.ProtobufAdapterHandler;
import io.github.vevoly.atomicio.codec.protobuf.ProtobufMessage;
import io.github.vevoly.atomicio.codec.protobuf.proto.GenericMessage;
import io.github.vevoly.atomicio.codec.protobuf.proto.Heartbeat;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.codec.AtomicIOServerCodecProvider;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;

/**
 * Protobuf 协议的 CodecProvider 实现。
 *
 * @since 0.3.0
 * @author vevoly
 */
public class ProtobufServerCodecProvider implements AtomicIOServerCodecProvider {

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
    public ChannelHandler getFrameDecoder(int maxFrameLength) {
        return new ProtobufVarint32FrameDecoder(maxFrameLength);
    }

    @Override
    public AtomicIOMessage getHeartbeat() {
        Heartbeat heartbeat = Heartbeat.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .build();
        return ProtobufMessage.of(AtomicIOCommand.HEARTBEAT, heartbeat);
    }

    @Override
    public AtomicIOMessage createHeartbeatResponse(AtomicIOMessage requestMessage) {
        try {
            Heartbeat heartbeatRequest = Heartbeat.parseFrom(requestMessage.getPayload());
            Heartbeat heartbeatResponse = Heartbeat.newBuilder()
                    .setTimestamp(heartbeatRequest.getTimestamp())
                    .build();
            AtomicIOMessage response = ProtobufMessage.of(AtomicIOCommand.HEARTBEAT, heartbeatResponse);
            return response;
        } catch (InvalidProtocolBufferException e) {
            // 解析失败
            throw new RuntimeException(e);
        }
    }

    @Override
    public void buildPipeline(ChannelPipeline pipeline, AtomicIOEngine engine) {

        DefaultAtomicIOEngine atomicIOEngine = (DefaultAtomicIOEngine) engine;
        // Outbound
        pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
        pipeline.addLast(getEncoder());


        // Inbound
//        pipeline.addLast("A_Logger", new LoggingHandler(LogLevel.INFO)); // 窃听点 A: 原始数据流（仅用于调试）
        pipeline.addLast(getFrameDecoder(atomicIOEngine.getConfig().getCodec().getMaxFrameLength()));
//        pipeline.addLast("B_Logger", new LoggingHandler(LogLevel.INFO)); // 窃听点 B: 检查分帧结果
        pipeline.addLast(getDecoder());

        // 适配器
//        pipeline.addLast("C_Logger", new LoggingHandler(LogLevel.INFO)); // 窃听点 C: 检查 Protobuf 解码结果
        pipeline.addLast(new ProtobufAdapterHandler());
//        pipeline.addLast("D_Logger", new LoggingHandler(LogLevel.INFO)); // 窃听点 D: 检查适配器转换结果
    }

}