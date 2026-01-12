package io.github.vevoly.atomicio.id;

import io.github.vevoly.atomicio.common.api.id.AtomicIOIdGenerator;

import java.util.UUID;

/**
 * UUID ID 生成器
 * 这是一个简单的 uuid 实现，取 uuid 高位，会有冲突概率
 * 仅适用于非极端环境
 *
 * @since 0.6.1
 * @author vevoly
 *
 */
public class UuidIdGenerator implements AtomicIOIdGenerator {
    @Override
    public long nextId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
