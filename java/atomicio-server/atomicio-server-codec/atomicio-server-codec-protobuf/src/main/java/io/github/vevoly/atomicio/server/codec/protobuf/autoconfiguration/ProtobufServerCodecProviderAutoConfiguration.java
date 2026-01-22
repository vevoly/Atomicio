package io.github.vevoly.atomicio.server.codec.protobuf.autoconfiguration;

import io.github.vevoly.atomicio.protocol.api.codec.AtomicIOPayloadParser;
import io.github.vevoly.atomicio.server.api.codec.AtomicIOServerCodecProvider;
import io.github.vevoly.atomicio.server.codec.protobuf.ProtobufPayloadParser;
import io.github.vevoly.atomicio.server.codec.protobuf.ProtobufServerCodecProvider;
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
@ConditionalOnClass(io.github.vevoly.atomicio.server.codec.protobuf.ProtobufServerCodecProvider.class)
public class ProtobufServerCodecProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AtomicIOServerCodecProvider.class)
    public AtomicIOServerCodecProvider protobufCodecProvider() {
        log.info("AtomicIO: 启用 ProtobufCodecProvider");
        return new ProtobufServerCodecProvider();
    }

    @Bean
    @ConditionalOnMissingBean(AtomicIOPayloadParser.class)
    public AtomicIOPayloadParser protobufPayloadParser() {
        log.info("AtomicIO: 启用 ProtobufPayloadParser");
        return new ProtobufPayloadParser();
    }
}
