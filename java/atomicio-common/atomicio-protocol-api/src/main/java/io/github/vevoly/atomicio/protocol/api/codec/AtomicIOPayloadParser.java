package io.github.vevoly.atomicio.protocol.api.codec;

import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;

/**
 * 消息解析器
 *
 * @since 0.6.7
 * @author vevoly
 */
public interface AtomicIOPayloadParser {

    /**
     * 将 AtomicIOMessage 的二进制 payload 解析为指定的目标 Class 类型。
     *
     * @param message 要解析的消息对象
     * @param clazz   目标类型的 Class 对象
     * @param <T>     目标类型
     * @return 解析成功后的目标类型实例
     * @throws Exception 如果解析失败
     */
    <T> T parse(AtomicIOMessage message, Class<T> clazz) throws Exception;

    /**
     * 将 payload 解析为一个简单的 String 值。
     * 专门用于像 JOIN_GROUP, LEAVE_GROUP 这种载体只是一个字符串的场景。
     *
     * @param message 要解析的消息
     * @return payload 代表的字符串值
     * @throws Exception 如果 payload 不是一个简单的字符串载体
     */
    String parseAsString(AtomicIOMessage message) throws Exception;
}
