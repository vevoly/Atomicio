package io.github.vevoly.atomicio.server.codec.text.autoconfiguration;

import io.github.vevoly.atomicio.protocol.api.codec.AtomicIOPayloadParser;
import io.github.vevoly.atomicio.server.api.codec.AtomicIOServerCodecProvider;
import io.github.vevoly.atomicio.server.codec.text.TextPayloadParser;
import io.github.vevoly.atomicio.server.codec.text.TextServerCodecProvider;
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
@ConditionalOnClass(io.github.vevoly.atomicio.server.codec.text.TextServerCodecProvider.class)
public class TextServerCodecProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AtomicIOServerCodecProvider.class)
    public AtomicIOServerCodecProvider textCodecProvider() {
        log.info("AtomicIO: 启用 TextCodecProvider");
        return new TextServerCodecProvider();
    }

    @Bean
    @ConditionalOnMissingBean(AtomicIOPayloadParser.class)
    public AtomicIOPayloadParser textPayloadParser() {
        log.info("AtomicIO: 启用 TextPayloadParser");
        return new TextPayloadParser();
    }
}
