package io.github.vevoly.atomicio.core.handler;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.vevoly.atomicio.core.utils.IpUtils;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.constants.ConnectionRejectType;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP 连接速率限制处理器
 *
 * @since 0.5.11
 * @author vevoly
 */
@Slf4j
@ChannelHandler.Sharable
public class IpRateLimitHandler extends ChannelInboundHandlerAdapter {

    private final AtomicIOEngine engine;
    private final int rateLimitCount;
    private final int rateLimitInterval;
    private final Cache<String, AtomicInteger> ipConnectionRates;

    public IpRateLimitHandler(AtomicIOEngine engine) {
        this.engine = engine;
        this.rateLimitCount = engine.getConfig().getIpSecurity().getRateLimitCount();
        this.rateLimitInterval = engine.getConfig().getIpSecurity().getRateLimitInterval();

        this.ipConnectionRates = Caffeine.newBuilder()
                .expireAfterWrite(rateLimitInterval, TimeUnit.SECONDS)
                .build();
    }

    /**
     * handlerAdded 在 Channel 被注册到 EventLoop 后，channelActive 之前被调用。
     * 在这个时间点关闭 Channel，可以更早地拒绝连接。
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        String ip = IpUtils.getIp(ctx);
        if (ip == null) {
            super.channelActive(ctx);
            return;
        }

        // 如果 key 不存在，则原子地计算并存入
        AtomicInteger count = ipConnectionRates.get(ip, k -> new AtomicInteger(0));
        if (count.incrementAndGet() > rateLimitCount) {
            log.warn("IP {} exceeded rate limit of {} conn/{}s. Rejecting connection.", ip, rateLimitCount, rateLimitInterval);
            // 触发通用拒绝事件
            engine.getEventManager().fireConnectionRejectEvent(ctx.channel(), ConnectionRejectType.CONNECTION_RATE_LIMIT_EXCEEDED, null);
            // 在 handlerAdded 中关闭，连接不会进入 active 状态
            ctx.channel().config().setOption(ChannelOption.SO_LINGER, 0);
            ctx.close();
        } else {
            // 未超限，允许连接
            super.handlerAdded(ctx);
        }
    }

}
