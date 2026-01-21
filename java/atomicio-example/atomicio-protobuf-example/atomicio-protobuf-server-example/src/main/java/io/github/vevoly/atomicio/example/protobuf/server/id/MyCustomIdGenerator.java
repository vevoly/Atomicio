package io.github.vevoly.atomicio.example.protobuf.server.id;

import io.github.vevoly.atomicio.common.api.exception.IdGenerationException;
import io.github.vevoly.atomicio.common.api.id.AtomicIOIdGenerator;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 自定义 ID 生成器
 * 可对接用户自己的 ID 服务器客户端
 *
 * @since 0.6.1
 * @author vevoly
 */
@Slf4j
public class MyCustomIdGenerator implements AtomicIOIdGenerator {

    private final AtomicLong sequence = new AtomicLong(999000000L);
    private final String prefix;

    public MyCustomIdGenerator(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public long nextId() {
        log.info("====== Custom ID Generator is working! Prefix: " + prefix + " ======");
        return sequence.incrementAndGet();
    }
}
