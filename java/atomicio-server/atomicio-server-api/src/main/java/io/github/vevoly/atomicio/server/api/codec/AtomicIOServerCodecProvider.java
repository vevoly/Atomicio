package io.github.vevoly.atomicio.server.api.codec;

import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.netty.channel.ChannelHandler;

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
     * 根据一个收到的请求消息，创建一个与之协议匹配的响应消息。
     * 这是框架实现协议无关响应的关键。FrameworkCommandDispatcher 将调用此方法
     * 来生成如 LOGIN_RESPONSE, JOIN_GROUP_RESPONSE 等框架级响应。
     *
     * @param requestMessage 触发响应的原始请求消息。实现类可以用它来获取 sequenceId 等元数据。
     * @param commandId      新响应消息的指令ID。
     * @param payload        新响应消息的载体。其类型取决于具体协议的实现。
     *                       - 对于文本类协议，通常是 String。
     *                       - 对于 Protobuf 协议，通常是 com.google.protobuf.Message 对象。
     * @return 一个实现了 AtomicIOMessage 接口的、可被发送的响应消息实例。
     */
    AtomicIOMessage createResponse(AtomicIOMessage requestMessage, int commandId, Object payload);

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