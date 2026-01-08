package io.github.vevoly.atomicio.core.session;

import io.github.vevoly.atomicio.api.AtomicIOEngine;
import io.github.vevoly.atomicio.api.AtomicIOSession;
import io.github.vevoly.atomicio.api.AtomicIOMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;

import java.util.Objects;
import java.util.concurrent.Future;

/**
 * AtomicIOSession 的默认实现，基于 Netty Channel。
 * 这个类是连接我们抽象 API 和 Netty 底层实现的桥梁。
 *
 * @since 0.0.1
 * @author vevoly
 */
public class NettySession implements AtomicIOSession {

    /**
     * Netty 的 Channel 是实际的网络连接管道
     */
    private final Channel channel;

    /**
     * IO 引擎
     * Session 持有 IO 引擎
     */
    private final AtomicIOEngine engine;

    private final long creationTime = System.currentTimeMillis();

    public NettySession(Channel channel, AtomicIOEngine engine) {
        this.channel = Objects.requireNonNull(channel, "Channel cannot be null");
        this.engine = Objects.requireNonNull(engine, "Engine cannot be null");
    }

    @Override
    public String getId() {
        // 使用 Netty Channel 的唯一 ID
        return channel.id().asLongText();
    }

    @Override
    public ChannelFuture send(AtomicIOMessage message) {
        return channel.writeAndFlush(message);
    }

    @Override
    public void close() {
        channel.close();
    }

    @Override
    public boolean isActive() {
        return channel.isActive();
    }

    @Override
    public AtomicIOEngine getEngine() {
        return this.engine;
    }

    @Override
    public String getRemoteAddress() {
        return channel.remoteAddress().toString();
    }

    @Override
    public long getCreationTime() {
        return this.creationTime;
    }

    @Override
    public long getLastActivityTime() {
        // todo 需要配合 Netty 的 IdleStateHandler 或自定义的 Handler 来更新时间戳
        // 先返回一个简单实现，后续再完善
        Long lastActivity = getAttribute("lastActivity");
        return lastActivity != null ? lastActivity : creationTime;
    }

    @Override
    public void setAttribute(String key, Object value) {
        AttributeKey<Object> attributeKey = AttributeKey.valueOf(key);
        channel.attr(attributeKey).set(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        AttributeKey<Object> attrKey = AttributeKey.valueOf(key);
        return (T) channel.attr(attrKey).get();
    }

    public Channel getNettyChannel() {
        return channel;
    }
}
