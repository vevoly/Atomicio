package io.github.vevoly.atomicio.protocol.api.routing;

import java.util.List;
import java.util.Set;

/**
 * 协议无关的转发信封接口
 * 它封装了执行一次消息转发操作所需的所有路由信息和业务载荷。
 *
 * @since 0.6.9
 * @author vevoly
 */
public interface AtomicIOForwardingEnvelope {

    /**
     * 获取转发用户 ID 列表
     * @return
     */
    List<String> getToUserIds();

    /**
     * 获取转发群组 ID
     * @return
     */
    String getToGroupId();

    /**
     * 获取需要排除的用户 ID 列表
     * @return
     */
    Set<String> getExcludeUserIds();

    /**
     * 获取业务载荷类型
     * @return
     */
    int getBusinessPayloadType();

    /**
     * 获取业务载荷
     * 这是一个由协议解码器解码后的、高层次的 Java 对象
     * 可能是 Protobuf Message, Map<String, Object>, String, 或任何 POJO。
     */
    Object getBusinessPayload();
}
