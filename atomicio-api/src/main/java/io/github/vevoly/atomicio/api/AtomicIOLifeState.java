package io.github.vevoly.atomicio.api;

/**
 * 引擎生命周期状态
 *
 * @since 0.0.7
 * @author vevoly
 */
public enum AtomicIOLifeState {
    /**
     * 引擎刚被创建，还未启动。
     */
    NEW,
    /**
     * 启动流程正在进行中
     */
    STARTING,
    /**
     * 引擎已成功启动，正在运行
     */
    RUNNING,
    /**
     * 关闭流程正在进行中
     */
    SHUTTING_DOWN,
    /**
     * 引擎已成功关闭
     */
    SHUTDOWN
}
