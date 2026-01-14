package io.github.vevoly.atomicio.server.api.codec;

import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.protocol.api.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;

import java.util.List;

/**
 * 编解码器提供者的顶层接口。
 * <p>
 * 框架通过这个接口获取用于 ChannelPipeline 的编码器和解码器实例。
 * 每个实现类都代表了一套具体的编解码协议。
 *
 * @version 0.2.0
 * @since 0.2.0
 * @author vevoly
 */
public interface AtomicIOServerCodecProvider {

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

    /**
     * 获取所有【入站】的协议相关 Handler。
     */
    List<ChannelHandler> getInboundHandlers(AtomicIOProperties config);

    /**
     * 获取所有【出站】的协议相关 Handler。
     */
    List<ChannelHandler> getOutboundHandlers(AtomicIOProperties config);

    /**
     * 将 AtomicIOMessage 对象编码为最终的、可在网络上传输的二进制字节数组。
     * 这个方法封装了特定协议的所有出站编码逻辑，包括添加长度头等。
     * 用于集群消息转发时的“预编码”。
     *
     * @param message 要编码的消息
     * @param config  配置文件
     * @return 编码后的字节数组
     * @throws Exception if encoding fails.
     */
    byte[] encodeToBytes(AtomicIOMessage message, AtomicIOProperties config) throws Exception;

}