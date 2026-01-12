package io.github.vevoly.atomicio.core.ssl;

import io.github.vevoly.atomicio.server.api.config.AtomicIOProperties;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ResourceUtils;

import javax.net.ssl.SSLException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * 服务器端 SslContext 的工厂类。
 * 负责根据配置创建 Netty 的 SslContext 实例。
 *
 * @since 0.5.3
 * @author vevoly
 */
@Slf4j
public final class SslContextFactory {

    private SslContextFactory() {}

    /**
     * 根据配置创建服务器端的 SslContext。
     *
     * @param sslConfig SSL 配置对象
     * @return a configured SslContext instance, or null if SSL is disabled.
     */
    public static SslContext createSslContext(AtomicIOProperties.Ssl sslConfig) {
        if (sslConfig == null || !sslConfig.isEnabled()) {
            return null;
        }
        if (sslConfig.getCertChainPath() == null || sslConfig.getPrivateKeyPath() == null) {
            try {
                throw new SSLException("SSL/TLS 已开启. 但是没有提供证书文件路径.");
            } catch (SSLException e) {
                throw new RuntimeException(e);
            }
        }

        // 使用用户提供的证书文件
        log.info("SSL/TLS 已开启. Loading certificate from specified paths...");
        log.debug("Certificate Path: {}", sslConfig.getCertChainPath());
        log.debug("Private Key Path: {}", sslConfig.getPrivateKeyPath());
        try {
            InputStream certStream = getInputStream(sslConfig.getCertChainPath());
            InputStream keyStream = getInputStream(sslConfig.getPrivateKeyPath());
            return SslContextBuilder.forServer(certStream, keyStream, sslConfig.getPrivateKeyPassword())
                    .build();
        } catch (SSLException | FileNotFoundException e) {
            log.error("Failed to build SslContext from provided certificate files.", e);
            throw new RuntimeException("Failed to initialize SSL/TLS.", e);
        }

    }

    /**
     * 从 classpath 或文件系统中加载资源。
     * Spring 的 ResourceUtils 提供了强大的能力。
     *
     * @param path 资源路径 (e.g., "classpath:server.crt", "file:/path/to/server.crt")
     * @return an InputStream for the resource.
     * @throws FileNotFoundException if the resource is not found.
     */
    private static InputStream getInputStream(String path) throws FileNotFoundException {
        try {
            return ResourceUtils.getURL(path).openStream();
        } catch (IOException e) {
            log.error("加载证书文件失败，请检查证书路径: {}", path);
            throw new RuntimeException(e);
        }
    }
}
