package io.github.vevoly.atomicio.core.utils;

import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;

public class IpUtils {

    public static String getIp(ChannelHandlerContext ctx) {
        try {
            return ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
        } catch (Exception e) {
            return null; // 无法获取 IP，则不进行过滤
        }
    }

}
