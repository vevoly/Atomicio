package io.github.vevoly.atomicio.client.api.listeners;

/**
 * 客户端错误监听器
 *
 * @since 0.5.0
 * @author vevoly
 *
 */
@FunctionalInterface
public interface OnErrorListener {
    void onError(Throwable cause);
}
