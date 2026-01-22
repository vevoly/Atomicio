//package io.github.vevoly.atomicio.starter.autoconfiguration;
//
//import io.github.vevoly.atomicio.common.api.config.AtomicIOConfigDefaultValue;
//import io.github.vevoly.atomicio.protocol.api.codec.AtomicIOPayloadParser;
//import io.github.vevoly.atomicio.server.api.codec.AtomicIOServerCodecProvider;
//import io.github.vevoly.atomicio.server.codec.protobuf.ProtobufPayloadParser;
//import io.github.vevoly.atomicio.server.codec.protobuf.ProtobufServerCodecProvider;
//import io.github.vevoly.atomicio.server.codec.text.TextServerCodecProvider;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
///**
// * AtomicIO 编码器自动配置类
// *
// * @since 0.6.2
// * @author vevoly
// */
//@Slf4j
//@Configuration
//// 顶层条件：只有在 Atomicio 引擎启用时，这个配置才生效
//@ConditionalOnProperty(prefix = AtomicIOConfigDefaultValue.CONFIG_PREFIX, name = "enabled", havingValue = "true")
//public class AtomicIOCodecAutoConfiguration {
//
//    /**
//     * 配置 A: Text Codec (可以作为备用或简单场景的默认)
//     */
//    @Configuration
//    @ConditionalOnClass(TextServerCodecProvider.class)
//    @ConditionalOnProperty(prefix = AtomicIOConfigDefaultValue.CONFIG_PREFIX_CODEC, name = "type", havingValue = "text", matchIfMissing = true) // 设为默认
//    static class TextCodecConfiguration {
//
//        @Bean
//        @ConditionalOnMissingBean(AtomicIOServerCodecProvider.class)
//        public AtomicIOServerCodecProvider textCodecProvider() {
//            log.info("AtomicIO: 启用 TextCodecProvider");
//            return new TextServerCodecProvider();
//        }
//    }
//
//    /**
//     * 配置 B: Protobuf Codec
//     */
//    @Configuration
//    @ConditionalOnClass(ProtobufServerCodecProvider.class)
//    @ConditionalOnProperty(prefix = AtomicIOConfigDefaultValue.CONFIG_PREFIX_CODEC, name = "type", havingValue = "protobuf")
//    static class ProtobufCodecConfiguration {
//
//        @Bean
//        @ConditionalOnMissingBean(AtomicIOServerCodecProvider.class)
//        public AtomicIOServerCodecProvider protobufCodecProvider() {
//            log.info("AtomicIO: 启用 ProtobufCodecProvider");
//            return new ProtobufServerCodecProvider();
//        }
//
//        @Bean
//        @ConditionalOnMissingBean(AtomicIOPayloadParser.class)
//        public AtomicIOPayloadParser protobufPayloadParser() {
//            log.info("AtomicIO: 启用 ProtobufPayloadParser");
//            return new ProtobufPayloadParser();
//        }
//    }
//}
