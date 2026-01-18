package io.github.vevoly.atomicio.server.api.listeners;

import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;

/**
 * 可写性改变监听器
 * 用于流量控制和背压。当 Netty 的底层 TCP 发送缓冲区从“满”变为“可写”时，会触发这个事件
 * 对于需要推送海量数据的场景（如文件传输、实时行情推送）非常重要。对于常规的 IM/游戏，则不那么重要。
 *
 * @since 0.1.3
 * @author vevoly
 */
@FunctionalInterface
public interface WritabilityChangedListener {

    void onWritabilityChanged(AtomicIOSession session);
}
