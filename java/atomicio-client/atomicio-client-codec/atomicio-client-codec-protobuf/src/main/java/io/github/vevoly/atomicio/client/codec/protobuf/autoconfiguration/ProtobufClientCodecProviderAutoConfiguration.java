package io.github.vevoly.atomicio.client.codec.protobuf.autoconfiguration;

import io.github.vevoly.atomicio.client.api.codec.AtomicIOClientCodecProvider;
import io.github.vevoly.atomicio.client.codec.protobuf.ProtobufClientCodecProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CodecProvider 自动选配
 *
 * @since 0.6.7
 * @author vevoly
 */
@Slf4j
@Configuration
@ConditionalOnClass(io.github.vevoly.atomicio.client.codec.protobuf.ProtobufClientCodecProvider.class)
public class ProtobufClientCodecProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AtomicIOClientCodecProvider.class)
    public AtomicIOClientCodecProvider protobufCodecProvider() {
        log.info("AtomicIO Client: 启用 ProtobufCodecProvider");
        return new ProtobufClientCodecProvider();
    }

}
