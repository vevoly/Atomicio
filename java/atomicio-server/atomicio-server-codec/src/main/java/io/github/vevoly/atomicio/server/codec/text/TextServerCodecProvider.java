package io.github.vevoly.atomicio.server.codec.text;

import io.github.vevoly.atomicio.codec.text.TextMessage;
import io.github.vevoly.atomicio.codec.text.TextMessageDecoder;
import io.github.vevoly.atomicio.codec.text.TextMessageEncoder;
import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.codec.AtomicIOServerCodecProvider;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;

import java.util.List;

/**
 * 文本协议的 CodecProvider 实现。
 *
 * @since 0.2.0
 * @author vevoly
 */
public class TextServerCodecProvider implements AtomicIOServerCodecProvider {

    private static final TextMessageEncoder ENCODER = new TextMessageEncoder();
    private static final TextMessageDecoder DECODER = new TextMessageDecoder();

    @Override
    public AtomicIOMessage createResponse(AtomicIOMessage requestMessage, int commandId, Object payload) {
        // 1. 安全检查和类型转换
        if (!(payload instanceof String)) {
            throw new IllegalArgumentException("TextCodecProvider expects a String payload, but got " +
                    (payload != null ? payload.getClass().getName() : "null"));
        }

        String content = (String) payload;
        String deviceId = null;

        // 2. 尝试从请求中继承 deviceId (如果适用)
        if (requestMessage instanceof TextMessage) {
            deviceId = ((TextMessage) requestMessage).getDeviceId();
        }

        // 3. 创建并返回一个新的 TextMessage 实例
        // 从原始请求中获取 sequenceId
        return new TextMessage(requestMessage.getSequenceId(), commandId, deviceId, content);
    }

    @Override
    public AtomicIOMessage createResponse(AtomicIOMessage requestMessage, int commandId, boolean success, String message) {
        String textPayload = (success ? "Success: " : "Error: ") + message;
        String deviceId = (requestMessage instanceof TextMessage) ? ((TextMessage) requestMessage).getDeviceId() : null;
        return new TextMessage(requestMessage.getSequenceId(), commandId, deviceId, textPayload);
    }

    /**
     * 获取所有【入站】的协议相关 Handler。
     * 顺序：分帧 -> 解码
     */
    @Override
    public List<ChannelHandler> getInboundHandlers(AtomicIOProperties config) {
        return List.of(
                // 1. LineBasedFrameDecoder: 负责按行切分，解决粘包/半包。
                new LineBasedFrameDecoder(config.getCodec().getMaxFrameLength()),
                // 2. TextMessageDecoder: 将一行 String 转换为 TextMessage 对象。
                DECODER
        );
    }

    /**
     * 获取所有【出站】的协议相关 Handler。
     */
    @Override
    public List<ChannelHandler> getOutboundHandlers(AtomicIOProperties config) {
        return List.of(
                // 1. TextMessageEncoder: 将 TextMessage 对象转换为 "id:content\n" 格式的 ByteBuf。
                ENCODER
        );
    }

    @Override
    public byte[] encodeToBytes(AtomicIOMessage message, AtomicIOProperties config) throws Exception {
        // 创建 EmbeddedChannel，并将此协议的出站 Handlers 添加进去。
        EmbeddedChannel channel = new EmbeddedChannel(
                getOutboundHandlers(config).toArray(new ChannelHandler[0])
        );

        try {
            // 将消息写入出站 Pipeline。
            if (!channel.writeOutbound(message)) {
                // 如果 writeOutbound 返回 false，意味着没有 Handler 处理这个消息类型
                throw new Exception("Message type not handled by outbound pipeline: " + message.getClass().getName());
            }
            // 从出站缓冲区读取编码后的结果。
            ByteBuf encoded = channel.readOutbound();
            if (encoded == null) {
                // 编码后没有产生任何字节
                return new byte[0];
            }
            // 将 ByteBuf 转换为 byte[]
            byte[] bytes = new byte[encoded.readableBytes()];
            encoded.readBytes(bytes);

            return bytes;
        } finally {
            // 确保所有资源被释放
            channel.finishAndReleaseAll();
        }
    }

}
