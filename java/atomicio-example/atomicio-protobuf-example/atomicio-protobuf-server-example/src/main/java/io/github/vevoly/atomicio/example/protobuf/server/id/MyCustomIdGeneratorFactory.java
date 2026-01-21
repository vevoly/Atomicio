package io.github.vevoly.atomicio.example.protobuf.server.id;

import io.github.vevoly.atomicio.common.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.common.api.id.AtomicIOIdGenerator;
import io.github.vevoly.atomicio.common.api.id.AtomicIOIdGeneratorFactory;

/**
 * 自定义 id 生成器工厂
 * 测试自定义 id 生成器
 *
 * @since 0.6.1
 * @author vevoly
 */
public class MyCustomIdGeneratorFactory implements AtomicIOIdGeneratorFactory {

    // 必须有一个公共的、无参数的构造函数！
    public MyCustomIdGeneratorFactory() {}

    @Override
    public AtomicIOIdGenerator create(AtomicIOProperties properties) throws Exception {
        String factoryClass = properties.getIdGen().getCustomFactoryClass();
        return new MyCustomIdGenerator(factoryClass);
    }
}
