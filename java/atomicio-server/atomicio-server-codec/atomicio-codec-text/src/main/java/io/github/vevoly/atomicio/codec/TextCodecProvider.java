package io.github.vevoly.atomicio.codec;

import io.github.vevoly.atomicio.codec.text.TextMessage;
import io.github.vevoly.atomicio.common.api.AtomicIOCommand;
import io.github.vevoly.atomicio.common.api.AtomicIOMessage;
import io.github.vevoly.atomicio.codec.text.TextMessageDecoder;
import io.github.vevoly.atomicio.codec.text.TextMessageEncoder;
import io.github.vevoly.atomicio.server.api.AtomicIOCodecProvider;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LineBasedFrameDecoder;

/**
 * 文本协议的 CodecProvider 实现。
 *
 * @since 0.2.0
 * @author vevoly
 */
public class TextCodecProvider implements AtomicIOCodecProvider {

    @Override
    public ChannelHandler getEncoder() {
        // 为了线程安全，每次调用都应该返回一个新的实例
        return new TextMessageEncoder();
    }

    @Override
    public ChannelHandler getDecoder() {
        // 返回文本解码器实例
        return new TextMessageDecoder();
    }

    @Override
    public ChannelHandler getFrameDecoder(int maxFrameLength) {
        // 文本协议需要 LineBasedFrameDecoder 来按行分帧
        return new LineBasedFrameDecoder(maxFrameLength);
    }

    @Override
    public AtomicIOMessage createHeartbeatResponse(AtomicIOMessage requestMessage) {
        return new TextMessage(AtomicIOCommand.HEARTBEAT, "PONG");
    }

    @Override
    public void buildPipeline(ChannelPipeline pipeline, int maxFrameLength) {
        pipeline.addLast(getFrameDecoder(maxFrameLength));
        pipeline.addLast(getDecoder());
        pipeline.addLast(getEncoder());
    }
}
