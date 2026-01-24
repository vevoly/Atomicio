package io.github.vevoly.atomicio.server.api.manager;

import java.util.concurrent.Future;

/**
 * 传输管理器接口
 *
 * @since 0.6.8
 * @author vevoly
 */
public interface TransportManager {

    /**
     * 启动传输管理器
     * @return
     */
    Future<Void> start();

    /**
     * 关闭传输管理器
     */
    void stop();
}
