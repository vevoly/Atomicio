package io.github.vevoly.atomicio.client.codec.protobuf;

import io.github.vevoly.atomicio.client.api.codec.AtomicIOClientCodecProvider;
import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.codec.decoder.ProtobufVarint32FrameDecoder;
import io.github.vevoly.atomicio.codec.protobuf.ProtobufAdapterHandler;
import io.github.vevoly.atomicio.codec.protobuf.ProtobufMessage;
import io.github.vevoly.atomicio.codec.protobuf.proto.GenericMessage;
import io.github.vevoly.atomicio.codec.protobuf.proto.Heartbeat;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;

/**
 * Protobuf 协议的 CodecProvider 客户端实现。
 *
 * @since 0.6.2
 * @author vevoly
 */
public class ProtobufClientCodecProvider implements AtomicIOClientCodecProvider {
    @Override
    public ChannelHandler getEncoder() {
        return new ProtobufEncoder();
    }

    @Override
    public ChannelHandler getDecoder() {
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
        return ProtobufMessage.of(AtomicIOCommand.HEARTBEAT_REQUEST, heartbeat);
    }

    @Override
    public void buildPipeline(ChannelPipeline pipeline, AtomicIOClientConfig config) {
        // Outbound
        pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
        pipeline.addLast(getEncoder());

        // Inbound
        pipeline.addLast(getFrameDecoder(config.getMaxFrameLength()));
        pipeline.addLast(getDecoder());

        // 适配器
        pipeline.addLast(new ProtobufAdapterHandler());
    }
}
