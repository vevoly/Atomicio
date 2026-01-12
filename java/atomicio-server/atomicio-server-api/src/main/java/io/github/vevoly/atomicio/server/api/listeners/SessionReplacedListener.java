package io.github.vevoly.atomicio.server.api.listeners;

import io.github.vevoly.atomicio.server.api.AtomicIOSession;

/**
 * 会话被替换监听器
 * 单点登录
 *
 * @since 0.5.2
 * @author vevoly
 */
@FunctionalInterface
public interface SessionReplacedListener {

    /**
     * 当一个旧的会话因为同一用户的新会话登录而被踢掉时触发。
     * @param oldSession 被踢掉的旧会话
     * @param newSession 导致踢出的新会话
     */
    void onSessionReplaced(AtomicIOSession oldSession, AtomicIOSession newSession);
}
