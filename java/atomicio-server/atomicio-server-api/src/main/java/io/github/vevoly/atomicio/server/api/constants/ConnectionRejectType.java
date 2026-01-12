package io.github.vevoly.atomicio.server.api.constants;

/**
 * 连接被拒绝的原因枚举
 *
 * @since 0.5.7
 * @author vevoly
 */
public enum ConnectionRejectType {

    /**
     * 单个 IP 的并发连接数超过了限制。
     */
    IP_CONNECTION_LIMIT_EXCEEDED,

    /**
     * 连接速率超过了限制。
     */
    CONNECTION_RATE_LIMIT_EXCEEDED,

    /**
     * SSL/TLS 握手失败。
     */
    SSL_HANDSHAKE_FAILED,

    /**
     * 服务器过载保护。
     */
    SERVER_OVERLOADED,

    /**
     * 其他未知原因。
     */
    UNKNOWN;
}
