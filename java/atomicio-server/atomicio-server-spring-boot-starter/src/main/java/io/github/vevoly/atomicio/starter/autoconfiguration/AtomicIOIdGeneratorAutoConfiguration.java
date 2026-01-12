package io.github.vevoly.atomicio.starter.autoconfiguration;

import io.github.vevoly.atomicio.common.api.config.AtomicIOConfigDefaultValue;
import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.common.api.id.AtomicIOIdGenerator;
import io.github.vevoly.atomicio.common.api.id.AtomicIOIdGeneratorFactory;
import io.github.vevoly.atomicio.id.SnowflakeIdGenerator;
import io.github.vevoly.atomicio.id.UuidIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ID 生成器自动装配类
 * 支持 雪花算法、用户自定义 ID 生成器
 *
 * @since 0.6.1
 * @author vevoly
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = AtomicIOConfigDefaultValue.CONFIG_PREFIX, name = "enabled", havingValue = "true")
public class AtomicIOIdGeneratorAutoConfiguration {

    /**
     * 配置类 A: 用于 Snowflake ID 生成器
     * 只有当 type=snowflake (或未配置type)，并且 SnowflakeIdGenerator 类在 classpath 中时，才生效。
     */
    @Configuration
    @ConditionalOnClass(SnowflakeIdGenerator.class)
    @ConditionalOnProperty(prefix = AtomicIOConfigDefaultValue.CONFIG_PREFIX_ID_GEN, name = "type", havingValue = "snowflake", matchIfMissing = true)
    static class SnowflakeConfiguration {

        @Bean
        @ConditionalOnMissingBean(AtomicIOIdGenerator.class)
        public AtomicIOIdGenerator snowflakeIdGenerator(AtomicIOProperties properties) {
            AtomicIOProperties.Snowflake snowflakeConfig = properties.getIdGen().getSnowflake();
            log.info("Creating default SnowflakeIdGenerator with workerId={} and datacenterId={}",
                    snowflakeConfig.getWorkerId(), snowflakeConfig.getDatacenterId());

            return new SnowflakeIdGenerator(
                    snowflakeConfig.getWorkerId(),
                    snowflakeConfig.getDatacenterId()
            );
        }
    }

    /**
     * 配置类 B: 用于 UUID ID 生成器
     * 只有当 type=uuid，并且 UuidIdGenerator 类在 classpath 中时，才生效。
     */
    @Configuration
    @ConditionalOnClass(UuidIdGenerator.class)
    @ConditionalOnProperty(prefix = AtomicIOConfigDefaultValue.CONFIG_PREFIX_ID_GEN, name = "type", havingValue = "uuid")
    static class UuidConfiguration {

        @Bean
        @ConditionalOnMissingBean(AtomicIOIdGenerator.class)
        public AtomicIOIdGenerator uuidIdGenerator() {
            log.info("Creating UuidIdGenerator.");
            return new UuidIdGenerator();
        }
    }

    /**
     * **配置类 C: 用于用户自定义的 ID 生成器**
     * 只有当 type=custom 时才生效。
     */
    @Configuration
    @ConditionalOnProperty(prefix = AtomicIOConfigDefaultValue.CONFIG_PREFIX_ID_GEN, name = "type", havingValue = "custom")
    static class CustomConfiguration {

        @Bean
        @ConditionalOnMissingBean(AtomicIOIdGenerator.class)
        public AtomicIOIdGenerator customIdGenerator(AtomicIOProperties properties) {
            String factoryClassName = properties.getIdGen().getCustomFactoryClass();
            if (factoryClassName == null || factoryClassName.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "'atomicio.id-gen.custom-factory-class' must be specified when 'atomicio.id-gen.type' is 'custom'."
                );
            }

            log.info("Creating custom IdGenerator using factory: {}", factoryClassName);
            try {
                // 通过反射创建 Factory 实例
                Class<?> factoryClass = Class.forName(factoryClassName);
                AtomicIOIdGeneratorFactory factory = (AtomicIOIdGeneratorFactory) factoryClass.getDeclaredConstructor().newInstance();
                // 使用 Factory 创建 IdGenerator 实例
                return factory.create(properties);
            } catch (Exception e) {
                log.error("Failed to create custom IdGenerator from factory '{}'", factoryClassName, e);
                throw new RuntimeException("Could not create custom IdGenerator", e);
            }
        }
    }
}
