package io.github.vevoly.atomicio.client.codec.text;

import io.github.vevoly.atomicio.client.api.codec.AtomicIOClientCodecProvider;
import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.codec.text.TextMessage;
import io.github.vevoly.atomicio.codec.text.TextMessageDecoder;
import io.github.vevoly.atomicio.codec.text.TextMessageEncoder;
import io.github.vevoly.atomicio.common.api.constants.AtomicIOConstant;
import io.github.vevoly.atomicio.protocol.api.AtomicIOCommand;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.protocol.api.result.AuthResult;
import io.github.vevoly.atomicio.protocol.api.result.GeneralResult;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LineBasedFrameDecoder;

import java.nio.charset.StandardCharsets;

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
        return new TextMessage(requestMessage.getSequenceId(), AtomicIOCommand.HEARTBEAT_REQUEST,  "",AtomicIOCommand.HEARTBEAT_REQUEST + ":PONG");
    }

    @Override
    public void buildPipeline(ChannelPipeline pipeline, AtomicIOClientConfig config) {
        pipeline.addLast(getFrameDecoder(config.getMaxFrameLength()));
        pipeline.addLast(getDecoder());
        pipeline.addLast(getEncoder());
    }

    @Override
    public AtomicIOMessage createRequest(long sequenceId, int commandId, String deviceId, Object... params) {
        String content = "";

        switch (commandId) {
            case AtomicIOCommand.LOGIN_REQUEST:
                // 对于登录， params 是 (String userId, String token)
                // 并将它们拼接成 "userId:token"
                content = params[0] + ":" + params[1];
                break;
            case AtomicIOCommand.JOIN_GROUP_REQUEST:
                // 对于加群，params 是 (String groupId)
                content = (String) params[0];
                break;
        }
        return new TextMessage(sequenceId, commandId, deviceId, content);
    }

    @Override
    public <T> T parsePayloadAs(AtomicIOMessage message, Class<T> clazz) throws Exception {
        return null;
    }

    @Override
    public AuthResult toAuthResult(AtomicIOMessage responseMessage, String originalUserId, String originalDeviceId) throws Exception {
        String content = new String(responseMessage.getPayload(), StandardCharsets.UTF_8);
        boolean success = content.startsWith(AtomicIOConstant.SUCCESS);
        return new AuthResult(success, originalUserId, originalDeviceId, content);
    }

    @Override
    public GeneralResult toGeneralResult(AtomicIOMessage responseMessage) throws Exception {
        String content = new String(responseMessage.getPayload(), StandardCharsets.UTF_8);
        boolean success = content.startsWith(AtomicIOConstant.SUCCESS);
        return new GeneralResult(success, content);
    }
}
