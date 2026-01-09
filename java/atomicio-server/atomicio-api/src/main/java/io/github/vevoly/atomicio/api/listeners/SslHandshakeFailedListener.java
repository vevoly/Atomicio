package io.github.vevoly.atomicio.api.listeners;

import io.netty.channel.Channel;

/**
 * 当一个入站连接的 SSL/TLS 握手失败时触发的监听器
 * 用户可以在此进行安全策略操作，如：记录日志、更新黑名单等
 * 传输层防御
 *
 * @since 0.5.4
 * @author vevoly
 */
@FunctionalInterface
public interface SslHandshakeFailedListener {

    /**
     * 在 SSL/TLS 握手失败时调用。
     *
     * @param channel 发生失败的 Netty Channel。注意，此时 AtomicIOSession 可能还未创建。
     * @param cause   导致握手失败的异常。
     */
    void onSslHandshakeFailed(Channel channel, Throwable cause);
}
