package io.github.vevoly.atomicio.client.codec.protobuf;

import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.github.vevoly.atomicio.client.api.codec.AtomicIOClientCodecProvider;
import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.codec.decoder.ProtobufVarint32FrameDecoder;
import io.github.vevoly.atomicio.codec.protobuf.ProtobufAdapterHandler;
import io.github.vevoly.atomicio.codec.protobuf.ProtobufMessage;
import io.github.vevoly.atomicio.codec.protobuf.proto.GenericMessage;
import io.github.vevoly.atomicio.codec.protobuf.proto.GenericResponse;
import io.github.vevoly.atomicio.codec.protobuf.proto.Heartbeat;
import io.github.vevoly.atomicio.codec.protobuf.proto.LoginRequest;
import io.github.vevoly.atomicio.codec.protobuf.proto.StringRequest;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.protocol.api.result.AuthResult;
import io.github.vevoly.atomicio.protocol.api.result.GeneralResult;
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
        return ProtobufMessage.of(0, AtomicIOCommand.HEARTBEAT_REQUEST, heartbeat);
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

    @Override
    public AtomicIOMessage createRequest(long sequenceId, int commandId, String deviceId, Object... params) {
        Message payload = null;

        switch (commandId) {
            case AtomicIOCommand.HEARTBEAT_REQUEST:
                // 对于心跳，params 是: (long timestamp)
                long timestamp = (long) params[0];
                payload = Heartbeat.newBuilder()
                        .setTimestamp(timestamp)
                        .build();
                break;
            case AtomicIOCommand.LOGIN_REQUEST:
                // 对于登录，params 是: (String userId, String token)
                String userId = (String) params[0];
                String token = (String) params[1];
                payload = LoginRequest.newBuilder()
                        .setUserId(userId)
                        .setToken(token)
                        .setDeviceId(deviceId)
                        .build();
                break;
            case AtomicIOCommand.LOGOUT_REQUEST:
                // 对于登出，params 是: 空
                payload = Empty.getDefaultInstance();
                break;
            case AtomicIOCommand.JOIN_GROUP_REQUEST:
            case AtomicIOCommand.LEAVE_GROUP_REQUEST:
                // 对于加/退群，params 是: (String groupId)
                String groupId = (String) params[0];
                payload = StringRequest.newBuilder().setValue(groupId).build();
                break;
            default:
                // 对于未知的业务命令，可以约定第一个参数就是 payload，例如 发送 p2p 消息、群消息
                if (params.length > 0 && params[0] instanceof Message) {
                    payload = (Message) params[0];
                }
                break;
        }
        // 使用 ProtobufMessage 的工厂方法创建最终的消息
        // 这里的 deviceId 在元数据层面对于 Protobuf 意义不大，可以忽略或传入
        return ProtobufMessage.of(sequenceId, commandId, payload);
    }

    @Override
    public <T> T parsePayloadAs(AtomicIOMessage message, Class<T> clazz) throws InvalidProtocolBufferException {
        if (message instanceof ProtobufMessage) {
            Any payload = ((ProtobufMessage) message).getAnyPayload();
            if (Message.class.isAssignableFrom(clazz)) {
                if (payload.is((Class<? extends Message>) clazz)) {
                    return (T) payload.unpack((Class<? extends Message>) clazz);
                }
            }
        }
        throw new InvalidProtocolBufferException("Payload type 不匹配.");
    }

    @Override
    public AuthResult toAuthResult(AtomicIOMessage responseMessage, String originalUserId, String originalDeviceId) throws Exception {
        GenericResponse genericResponse = parsePayloadAs(responseMessage, GenericResponse.class);
        // 构建 AuthResult
        return new AuthResult(
                genericResponse.getSuccess(),
                originalUserId,
                originalDeviceId,
                genericResponse.getMessage()
        );
    }

    @Override
    public GeneralResult toGeneralResult(AtomicIOMessage responseMessage) throws Exception {
        GenericResponse generalResponse = parsePayloadAs(responseMessage, GenericResponse.class);
        return new GeneralResult(generalResponse.getSuccess(), generalResponse.getMessage());
    }

}
