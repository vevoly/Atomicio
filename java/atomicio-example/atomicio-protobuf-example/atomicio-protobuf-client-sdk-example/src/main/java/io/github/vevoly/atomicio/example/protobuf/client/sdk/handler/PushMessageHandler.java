package io.github.vevoly.atomicio.example.protobuf.client.sdk.handler;

import io.github.vevoly.atomicio.client.api.codec.AtomicIOClientCodecProvider;
import io.github.vevoly.atomicio.example.protobuf.common.cmd.BusinessCommand;
import io.github.vevoly.atomicio.example.protobuf.proto.GroupMessageNotify;
import io.github.vevoly.atomicio.example.protobuf.proto.P2PMessageNotify;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * 处理服务器主动推送的业务消息
 *
 * @since 0.6.8
 * @author vevoly
 */
@Slf4j
public class PushMessageHandler {

    private final AtomicIOClientCodecProvider codecProvider;

    public PushMessageHandler(AtomicIOClientCodecProvider codecProvider) {
        this.codecProvider = codecProvider;
    }

    /**
     * 处理所有服务器主动推送的消息。
     * @param message 推送的消息
     */
    public void handle(AtomicIOMessage message) {
        try {
            switch (message.getCommandId()) {
                case BusinessCommand.P2P_MESSAGE_NOTIFY:
                    P2PMessageNotify p2pNotify = codecProvider.parsePayloadAs(message, P2PMessageNotify.class);
                    log.info("<<<<<<<<< [私聊] 收到来自 [{}]: {}", p2pNotify.getFromUserId(), p2pNotify.getContent());
                    break;
                case BusinessCommand.GROUP_MESSAGE_NOTIFY:
                    GroupMessageNotify groupNotify = codecProvider.parsePayloadAs(message, GroupMessageNotify.class);
                    log.info("<<<<<<<<< [群聊-{}] 收到来自 [{}]: {}", groupNotify.getGroupId(), groupNotify.getFromUserId(), groupNotify.getContent());
                    break;
                default:
                    log.warn("<<<<<<<<< 收到未处理的推送消息，指令ID: {}", message.getCommandId());
            }
        } catch (Exception e) {
            log.error("<<<<<<<<< 解析推送消息 payload 错误, 指令ID: {}", message.getCommandId(), e);
        }
    }
}
