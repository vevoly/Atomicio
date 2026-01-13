package io.github.vevoly.atomicio.client.codec.text;

import io.github.vevoly.atomicio.client.api.codec.AtomicIOClientCodecProvider;
import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.codec.text.TextMessage;
import io.github.vevoly.atomicio.codec.text.TextMessageDecoder;
import io.github.vevoly.atomicio.codec.text.TextMessageEncoder;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.AtomicIOMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LineBasedFrameDecoder;

/**
 * 文本协议的 CodecProvider 客户端实现。
 *
 * @since 0.6.2
 * @author vevoly
 */
public class TextClientCodecProvider implements AtomicIOClientCodecProvider {

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
    public void buildPipeline(ChannelPipeline pipeline, AtomicIOClientConfig config) {
        pipeline.addLast(getFrameDecoder(config.getMaxFrameLength()));
        pipeline.addLast(getDecoder());
        pipeline.addLast(getEncoder());
    }
}
