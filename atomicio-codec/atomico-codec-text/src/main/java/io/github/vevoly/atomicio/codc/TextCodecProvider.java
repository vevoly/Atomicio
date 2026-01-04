package io.github.vevoly.atomicio.codc;

import io.github.vevoly.atomicio.api.codec.AtomicIOCodecProvider;
import io.github.vevoly.atomicio.codc.text.TextMessageDecoder;
import io.github.vevoly.atomicio.codc.text.TextMessageEncoder;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.LineBasedFrameDecoder;

/**
 * 文本协议的 CodecProvider 实现。
 *
 * @since 0.2.0
 * @author vevoly
 */
public class TextCodecProvider implements AtomicIOCodecProvider {

    private static final int MAX_FRAME_LENGTH = 1024;

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
    public ChannelHandler getFrameDecoder() {
        // 文本协议需要 LineBasedFrameDecoder 来按行分帧
        return new LineBasedFrameDecoder(MAX_FRAME_LENGTH);
    }
}
