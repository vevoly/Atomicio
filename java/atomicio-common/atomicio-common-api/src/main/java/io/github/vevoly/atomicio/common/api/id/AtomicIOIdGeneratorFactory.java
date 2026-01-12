package io.github.vevoly.atomicio.common.api.id;

import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;

/**
 * ID 生成器工厂接口
 * 开发者可以在应用配置中指定该工厂的实现类，从而允许使用用户自定义的 ID 生成策略。
 *
 * @since 0.6.1
 * @author vevoly
 */
@FunctionalInterface
public interface AtomicIOIdGeneratorFactory {

    /**
     * 根据提供的应用配置属性创建一个 IdGenerator 实例。
     *
     * @param properties 全局应用属性，允许工厂在需要时访问其自定义的配置块。
     * @return 一个经过配置的 IdGenerator 新实例。
     * @throws Exception 如果工厂创建 IdGenerator 失败则抛出异常。
     */
    AtomicIOIdGenerator create(AtomicIOProperties properties) throws Exception;
}
