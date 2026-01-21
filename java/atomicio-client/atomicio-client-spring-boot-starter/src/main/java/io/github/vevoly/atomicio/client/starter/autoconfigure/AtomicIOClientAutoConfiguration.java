package io.github.vevoly.atomicio.client.starter.autoconfigure;

import io.github.vevoly.atomicio.client.api.AtomicIOClient;
import io.github.vevoly.atomicio.client.api.codec.AtomicIOClientCodecProvider;
import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.client.core.DefaultAtomicIOClient;
import io.github.vevoly.atomicio.client.starter.config.AtomicIOClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 客户端自动配置类
 *
 * @since 0.6.6
 * @author vevoly
 */
@Slf4j
@Configuration
public class AtomicIOClientAutoConfiguration {

    /**
     * 将 Properties 转换为 Core Config 的 Bean 定义
     * 这个 Bean 将被注入到下面的 atomicIOClient Bean 中。
     */
    @Bean
    @ConditionalOnMissingBean
    public AtomicIOClientConfig atomicIOClientConfig(AtomicIOClientProperties properties) {
        AtomicIOClientConfig coreConfig = new AtomicIOClientConfig();

        // 映射所有字段
        coreConfig.setServerHost(properties.getServerHost());
        coreConfig.setServerPort(properties.getServerPort());
        coreConfig.setConnectTimeoutMillis(properties.getConnectTimeoutMillis());
        coreConfig.setMaxFrameLength(properties.getMaxFrameLength());

        coreConfig.setHeartbeatEnabled(properties.isHeartbeatEnabled());
        coreConfig.setWriterIdleSeconds(properties.getWriterIdleSeconds());

        coreConfig.setReconnectEnabled(properties.isReconnectEnabled());
        coreConfig.setInitialReconnectDelaySeconds(properties.getInitialReconnectDelaySeconds());
        coreConfig.setMaxReconnectDelaySeconds(properties.getMaxReconnectDelaySeconds());

        // 处理 SSL 路径转换
        if (properties.getSsl().isEnabled() && properties.getSsl().getTrustCertPath() != null) {
            try {
                coreConfig.getSsl().setTrustCertFromResource(properties.getSsl().getTrustCertPath());
            } catch (Exception e) {
                log.error("Failed to load SSL trust certificate from resource: {}",
                        properties.getSsl().getTrustCertPath(), e);
                throw new RuntimeException(e);
            }
        }
        return coreConfig;
    }

    /**
     * 自动配置 AtomicIOClient Bean。
     * 现在它依赖于我们上面创建的 AtomicIOClientConfig Bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public AtomicIOClient atomicIOClient(
            AtomicIOClientConfig coreConfig,
            AtomicIOClientCodecProvider codecProvider
    ) {
        log.info("自动配置 AtomicIOClient...");
        // DefaultAtomicIOClient 只依赖于纯净的 coreConfig
        return new DefaultAtomicIOClient(coreConfig, codecProvider);
    }

}
