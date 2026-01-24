package io.github.vevoly.atomicio.core.handler;

import io.github.vevoly.atomicio.core.utils.IpUtils;
import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.constants.ConnectionRejectType;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP连接限制处理器
 *
 * @since 0.5.6
 * @author vevoly
 */
@Slf4j
@ChannelHandler.Sharable
public class IpConnectionLimitHandler extends ChannelInboundHandlerAdapter {

    /**
     * 每个 ip 最大连接数
     */
    private final int maxConnectionsPerIp;
    private final AtomicIOEngine engine;
    private final Map<String, AtomicInteger> ipConnectionCounts = new ConcurrentHashMap<>();

    public IpConnectionLimitHandler(AtomicIOEngine engine) {
        this.engine = engine;
        this.maxConnectionsPerIp = engine.getConfig().getIpSecurity().getMaxConnect();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (maxConnectionsPerIp <= 0) {
            // 如果限制为0或负数，则不进行限制
            super.channelActive(ctx);
            return;
        }

        String ip = IpUtils.getIp(ctx);
        if (ip == null) {
            super.channelActive(ctx);
            return;
        }

        // 原子地增加连接数并获取结果
        int currentConnections = ipConnectionCounts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        if (currentConnections > maxConnectionsPerIp) {
            log.warn("IP {} exceeded max connection limit of {}. Closing connection.", ip, maxConnectionsPerIp);
            engine.getEventManager().fireConnectionRejectEvent(ctx.channel(), ConnectionRejectType.IP_CONNECTION_LIMIT_EXCEEDED, null);
            // 超过限制，拒绝连接
            ctx.close();
        } else {
            log.debug("IP {} connected. Current connections: {}", ip, currentConnections);
            // 未超过限制，允许连接，将事件传递下去
            super.channelActive(ctx);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (maxConnectionsPerIp <= 0) {
            super.channelInactive(ctx);
            return;
        }

        String ip = IpUtils.getIp(ctx);
        if (ip != null) {
            // 原子地减少连接数
            ipConnectionCounts.computeIfPresent(ip, (k, v) -> {
                v.decrementAndGet();
                return v;
            });
        }
        super.channelInactive(ctx);
    }

}
