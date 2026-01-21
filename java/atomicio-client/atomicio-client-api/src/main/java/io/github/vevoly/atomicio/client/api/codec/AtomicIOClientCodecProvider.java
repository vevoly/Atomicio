package io.github.vevoly.atomicio.client.api.codec;

import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.protocol.api.result.AuthResult;
import io.github.vevoly.atomicio.protocol.api.result.GeneralResult;
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

    /**
     * 根据指令和载体，创建一个协议相关的请求消息。
     *
     * @param sequenceId 消息的序列号，由 RequestManager 生成
     * @param commandId  消息的指令ID
     * @param deviceId   当前客户端的设备ID
     * @param params     包含了构建业务载体所需的所有原始数据的可变参数数组
     * @return 一个实现了 AtomicIOMessage 接口的实例。
     */
    AtomicIOMessage createRequest(long sequenceId, int commandId, String deviceId, Object... params);

    /**
     * 解析 Payload 为指定类型的对象。
     * 将一个 AtomicIOMessage 的二进制 payload 解析为指定的目标 Class 类型
     * @param message       要解析的消息对象
     * @param clazz         目标类型 Class 对象
     * @return
     * @throws Exception    如果解析失败，抛出异常
     */
    <T> T parsePayloadAs(AtomicIOMessage message, Class<T> clazz) throws Exception;

    /**
     * 将认证响应消息转换为一个协议无关的 AuthResult 对象。
     *
     * @param responseMessage  收到的响应消息
     * @param originalUserId   原始请求中的 userId (用于填充 AuthResult)
     * @param originalDeviceId 原始请求中的 deviceId (用于填充 AuthResult)
     * @return 一个 AuthResult 实例
     * @throws Exception 如果响应消息不是一个有效的认证响应
     */
    AuthResult toAuthResult(AtomicIOMessage responseMessage, String originalUserId, String originalDeviceId) throws Exception;

    /**
     * 将通用的响应消息转换为一个协议无关的 GeneralResult 对象。
     *
     * @param responseMessage 收到的响应消息
     * @return 一个 GeneralResult 实例
     * @throws Exception 如果响应消息不是一个有效的通用响应
     */
    GeneralResult toGeneralResult(AtomicIOMessage responseMessage) throws Exception;
}
