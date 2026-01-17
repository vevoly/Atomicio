package io.github.vevoly.atomicio.server.api.state;

/**
 * 状态提供器
 *
 * @since 0.6.4
 * @author vevoly
 */
public interface AtomicIOStateProvider {

    /**
     * 启动
     */
    void start();

    /**
     * 关闭
     */
    void shutdown();

    /**
     * 获取会话状态提供器
     * @return
     */
    AtomicIOSessionStateProvider getSessionStateProvider();

    /**
     * 获取群组状态提供器
     * @return
     */
    AtomicIOGroupStateProvider getGroupStateProvider();

}
