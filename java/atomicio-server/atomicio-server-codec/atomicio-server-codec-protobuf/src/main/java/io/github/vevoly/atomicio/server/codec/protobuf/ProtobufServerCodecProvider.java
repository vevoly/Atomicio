package io.github.vevoly.atomicio.server.codec.protobuf;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.github.vevoly.atomicio.codec.decoder.ProtobufVarint32FrameDecoder;
import io.github.vevoly.atomicio.codec.protobuf.ProtobufAdapterHandler;
import io.github.vevoly.atomicio.codec.protobuf.ProtobufMessage;
import io.github.vevoly.atomicio.codec.protobuf.proto.GenericMessage;
import io.github.vevoly.atomicio.codec.protobuf.proto.GenericResponse;
import io.github.vevoly.atomicio.codec.protobuf.proto.PushMessage;
import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.codec.AtomicIOServerCodecProvider;
import io.github.vevoly.atomicio.server.api.session.AtomicIOBindRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Protobuf 协议的 CodecProvider 实现。
 *
 * @since 0.3.0
 * @author vevoly
 */
@Slf4j
public class ProtobufServerCodecProvider implements AtomicIOServerCodecProvider {

    @Override
    public AtomicIOMessage createResponse(AtomicIOMessage requestMessage, int commandId, Object payload) {
        if (!(payload instanceof Message)) {
            // 对于 Protobuf，如果 payload 不是 Message 类型，几乎无法继续
            throw new IllegalArgumentException("ProtobufCodecProvider expects a com.google.protobuf.Message payload.");
        }

        Message protoPayload = (Message) payload;
        // 使用 ProtobufMessage 的工厂方法来创建
        // 从原始请求中获取 sequenceId
        return ProtobufMessage.of(requestMessage.getSequenceId(), commandId, protoPayload);
    }

    @Override
    public AtomicIOMessage createResponse(AtomicIOMessage requestMessage, int commandId, boolean success, String message) {
        // Protobuf 协议，“成功/失败”的响应需要被包装成 GenericResponse
        GenericResponse protoPayload = GenericResponse.newBuilder()
                .setSuccess(success)
                .setMessage(message)
                .build();
        return ProtobufMessage.of(requestMessage.getSequenceId(), commandId, protoPayload);
    }

    @Override
    public AtomicIOMessage createResponse(AtomicIOBindRequest request, int commandId, String message) {
        GenericResponse protoPayload = GenericResponse.newBuilder()
                .setSuccess(true)
                .setMessage(message)
                .build();
        return ProtobufMessage.of(0, commandId, protoPayload);
    }

    @Override
    public AtomicIOMessage createPushMessage(String fromUserId, String fromGroupId, int businessPayloadType, Object businessPayload) {
        if (!(businessPayload instanceof Message)) {
            throw new IllegalArgumentException("Protobuf protocol requires a Message payload.");
        }
        // 1. 将业务 Object 重新序列化为 bytes，以放入 PushMessage 的 payload
        byte[] bizPayloadBytes = ((Message) businessPayload).toByteArray();
        // 2. 构建框架级的 PushMessage
        PushMessage.Builder pushBuilder = PushMessage.newBuilder()
                .setFromUserId(fromUserId)
                .setBusinessPayloadType(businessPayloadType)
                .setBusinessPayload(ByteString.copyFrom(bizPayloadBytes));
        if (fromGroupId != null) {
            pushBuilder.setFromGroupId(fromGroupId);
        }
        // 3. 使用 ProtobufMessage.of 工厂方法创建最终消息
        return ProtobufMessage.of(0, AtomicIOCommand.PUSH_MESSAGE, pushBuilder.build());
    }

    @Override
    public List<ChannelHandler> getInboundHandlers(AtomicIOProperties config) {
        return List.of(
                new ProtobufVarint32FrameDecoder(config.getCodec().getMaxFrameLength()),
                new ProtobufDecoder(GenericMessage.getDefaultInstance()),
                new ProtobufAdapterHandler()
        );
    }

    @Override
    public List<ChannelHandler> getOutboundHandlers(AtomicIOProperties config) {
        return List.of(
                // Adapter Handler 也是出站的一部分, 它负责将 AtomicIOMessage -> GenericMessage
                new ProtobufVarint32LengthFieldPrepender(),
                new ProtobufEncoder(),
                new ProtobufAdapterHandler()
        );
    }

    @Override
    public byte[] encodeToBytes(AtomicIOMessage message, AtomicIOProperties config) throws Exception {
        // 创建 EmbeddedChannel, 内存中运行，没有实际的网络连接
        EmbeddedChannel channel = new EmbeddedChannel(getOutboundHandlers(config).toArray(new ChannelHandler[0]));

        try {
            if (!channel.writeOutbound(message)) {
                throw new Exception("Message type not handled by outbound pipeline: " + message.getClass().getName());
            }

            // 将消息写入出站 Pipeline。这将触发所有 Encoder 的执行
            ByteBuf encodedResult = null;
            for (;;) {
                // 从队列头部取出一个对象
                Object outbound = channel.readOutbound();
                // 如果缓冲区空了，就跳出循环
                if (outbound == null) {
                    break;
                }

                // 检查取出的对象是不是我们想要的最终结果 (ByteBuf)
                if (outbound instanceof ByteBuf) {
                    if (encodedResult != null) {
                        // 如果我们已经找到了一个 ByteBuf，又找到了另一个，说明 Pipeline 配置可能有问题
                        // 释放掉之前的，保留最后一个
                        ReferenceCountUtil.release(encodedResult);
                    }
                    encodedResult = (ByteBuf) outbound;
                } else {
                    // 如果是中间产物 (比如 GenericMessage)，我们必须释放它以防止内存泄漏
                    ReferenceCountUtil.release(outbound);
                }
            }
            if (encodedResult == null) {
                log.warn("Encoding message {} resulted in no bytes.", message.getCommandId());
                // 如果编码后没有产生任何字节（例如，消息是空的或无效的），则返回空数组
                return new byte[0];
            }
            // 将 ByteBuf 转换为 byte[]
            byte[] bytes = new byte[encodedResult.readableBytes()];
            encodedResult.readBytes(bytes);
            return bytes;
        } finally {
            // 释放所有资源
            if(channel.isOpen()) {
                channel.finishAndReleaseAll();
            }
        }
    }

}