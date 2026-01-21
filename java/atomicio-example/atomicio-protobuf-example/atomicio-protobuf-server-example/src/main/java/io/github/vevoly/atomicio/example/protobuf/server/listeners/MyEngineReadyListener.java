package io.github.vevoly.atomicio.example.protobuf.server.listeners;

import io.github.vevoly.atomicio.server.api.AtomicIOEngine;
import io.github.vevoly.atomicio.server.api.listeners.EngineReadyListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 自定义的引擎准备就绪监听器示例
 *
 * @since 0.4.2
 * @author vevoly
 */
@Slf4j
@Component
public class MyEngineReadyListener implements EngineReadyListener {

    @Override
    public void onEngineReady(AtomicIOEngine engine) {
        log.info("=====================================================");
        log.info(" 欢迎使用 Atomicio IO 引擎!");
        log.info(" Protobuf example 所有监听器已经注册完成，现在可以尝试连接！");
        log.info("=====================================================");
    }
}
