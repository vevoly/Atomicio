package io.github.vevoly.atomicio.client.api.listeners;

/**
 * 客户端重连监听器
 *
 * @since 0.5.0
 * @author vevoly
 */
@FunctionalInterface
public interface OnReconnectingListener {
    /**
     * @param attempt 当前是第几次尝试重连
     * @param delaySeconds 本次尝试将在多少秒后发起
     */
    void onReconnecting(int attempt, int delaySeconds);
}
