package io.github.vevoly.atomicio.example.protobuf.listeners;

import io.github.vevoly.atomicio.api.constants.ConnectionRejectType;
import io.github.vevoly.atomicio.api.listeners.ConnectionRejectListener;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * SSL 握手失败监听器
 *
 * @since 0.5.4
 * @author vevoly
 */
@Slf4j
@Component
public class MySecurityListener implements ConnectionRejectListener {

    // @Autowired private BlacklistService blacklistService;

    @Override
    public void onConnectionReject(Channel channel, ConnectionRejectType rejectType, Throwable cause) {
        SocketAddress remoteAddress = channel.remoteAddress();
        switch (rejectType) {
            case IP_CONNECTION_LIMIT_EXCEEDED:
                log.warn("IP 连接数超过限制: {}", remoteAddress);
                break;
            case CONNECTION_RATE_LIMIT_EXCEEDED:
                log.warn("IP 连接速率超过限制: {}", remoteAddress);
                break;
            case SSL_HANDSHAKE_FAILED:
                if (remoteAddress instanceof InetSocketAddress) {
                    String ip = ((InetSocketAddress) remoteAddress).getAddress().getHostAddress();
                    log.warn("SSL handshake failed for IP: {}. Adding to temporary blacklist.", ip);
                    // blacklistService.add(ip);
                }
                break;
            default:
                log.warn("Connection rejected: {}", remoteAddress);
                break;
        }


    }
}
