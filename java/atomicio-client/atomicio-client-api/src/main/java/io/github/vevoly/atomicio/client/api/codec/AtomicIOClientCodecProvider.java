package io.github.vevoly.atomicio.client.api.codec;

import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.protocol.api.AtomicIOMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;

/**
 * AtomicIO 客户端 CodecProvider 接口
 *
 * @since 0.6.2
 * @author vevoly
 */
public interface AtomicIOClientCodecProvider {

    /**
     * 获取一个 Netty 的编码器 (Encoder) 实例。
     * <p>
     * 这个 Handler 负责将出站的 {@link AtomicIOMessage} 对象
     * 转换为 {@link io.netty.buffer.ByteBuf}。
     * 它通常是 {@link io.netty.handler.codec.MessageToByteEncoder} 的子类。
     *
     * @return a new ChannelHandler instance for encoding.
     */
    ChannelHandler getEncoder();

    /**
     * 获取一个 Netty 的解码器 (Decoder) 实例。
     * <p>
     * 这个 Handler 负责将入站的 {@link io.netty.buffer.ByteBuf}
     * 转换为具体的 {@link AtomicIOMessage} 对象。
     * 它通常是 {@link io.netty.handler.codec.ByteToMessageDecoder} 或
     * {@link io.netty.handler.codec.MessageToMessageDecoder} 的子类。
     *
     * @return a new ChannelHandler instance for decoding.
     */
    ChannelHandler getDecoder();

    // **可选但推荐的高级功能**
    /**
     * 获取一个可选的帧解码器 (Frame Decoder)。
     * <p>
     * 帧解码器负责处理 TCP 的粘包和半包问题，确保后续的解码器能收到一个完整的消息包。
     * 常见的实现有 {@link io.netty.handler.codec.LengthFieldBasedFrameDecoder}
     * 或 {@link io.netty.handler.codec.LineBasedFrameDecoder}。
     * <p>
     * 如果协议本身是自定界的（例如 HTTP），或者解码器自己处理分帧，可以返回 null。
     *
     * @param maxFrameLength 消息的最大长度，注意：客户端要与服务器端对齐，否则会出现问题。
     *
     * @return a new ChannelHandler instance for frame decoding, or null if not needed.
     */
    ChannelHandler getFrameDecoder(int maxFrameLength);

    /**
     * 提供一个默认的心跳消息。
     * @return A default heartbeat message, or null if not supported.
     */
    default AtomicIOMessage getHeartbeat() {
        return null;
    }

    /**
     * 根据收到的心跳请求，创建一个心跳回应消息。
     * 心跳的 PING/PONG 逻辑可以由协议层自行处理。
     *
     * @param requestMessage The received heartbeat request message.
     * @return An AtomicIOMessage representing the heartbeat response (PONG),
     *         or null if no response should be sent.
     */
    default AtomicIOMessage createHeartbeatResponse(AtomicIOMessage requestMessage) {
        // 默认实现：原样返回，适用于简单的 Echo PING/PONG
        return requestMessage;
    }

    void buildPipeline(ChannelPipeline pipeline, AtomicIOClientConfig config);

}
