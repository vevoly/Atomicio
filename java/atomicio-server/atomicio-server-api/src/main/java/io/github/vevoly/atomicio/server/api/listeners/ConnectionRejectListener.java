package io.github.vevoly.atomicio.server.api.listeners;

import io.github.vevoly.atomicio.server.api.constants.ConnectionRejectType;
import io.netty.channel.Channel;

/**
 * 当一个入站连接因为各种安全或策略原因被拒绝时触发的监听器
 * 用户可以在此进行安全策略操作，如：记录日志、更新黑名单等
 *
 * @since 0.5.4
 * @author vevoly
 */
@FunctionalInterface
public interface ConnectionRejectListener {


    void onConnectionReject(Channel channel, ConnectionRejectType rejectType, Throwable cause);
}
