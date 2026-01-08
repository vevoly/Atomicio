package io.github.vevoly.atomicio.example.protobuf.listeners;

import io.github.vevoly.atomicio.api.listeners.SslHandshakeFailedListener;
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
public class MySecurityListener implements SslHandshakeFailedListener {

    // @Autowired private BlacklistService blacklistService;

    @Override
    public void onSslHandshakeFailed(Channel channel, Throwable cause) {
        SocketAddress remoteAddress = channel.remoteAddress();
        if (remoteAddress instanceof InetSocketAddress) {
            String ip = ((InetSocketAddress) remoteAddress).getAddress().getHostAddress();
            log.warn("SSL handshake failed for IP: {}. Adding to temporary blacklist.", ip);
            // blacklistService.add(ip);
        }
    }
}
