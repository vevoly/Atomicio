package io.github.vevoly.atomicio.server.api.listeners;

import io.github.vevoly.atomicio.server.api.AtomicIOEngine;

/**
 * 当引擎完全启动并准备好接受连接时触发的监听器。
 *
 * @since 0.1.4
 * @author vevoly
 */
@FunctionalInterface
public interface EngineReadyListener {

    /**
     * 在引擎就绪时调用。
     * @param engine a reference to the ready engine instance.
     */
    void onEngineReady(AtomicIOEngine engine);

}
