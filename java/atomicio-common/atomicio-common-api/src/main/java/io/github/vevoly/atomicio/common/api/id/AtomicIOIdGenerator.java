package io.github.vevoly.atomicio.common.api.id;

import io.github.vevoly.atomicio.common.api.exception.IdGenerationException;

/**
 * ID 生成器接口
 *
 * @since 0.6.1
 * @author vevoly
 */
@FunctionalInterface
public interface AtomicIOIdGenerator {

    /**
     * 生成并返回一个唯一 ID.
     *
     * @return 唯一 ID.
     */
    long nextId() throws IdGenerationException;
}
