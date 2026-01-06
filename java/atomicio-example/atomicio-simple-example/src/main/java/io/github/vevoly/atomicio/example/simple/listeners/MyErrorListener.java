package io.github.vevoly.atomicio.example.simple.listeners;

import io.github.vevoly.atomicio.api.AtomicIOSession;
import io.github.vevoly.atomicio.api.listeners.ErrorEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 自定义错误监听器
 *
 * @since 0.0.7
 * @author vevoly
 */
@Slf4j
@Component
public class MyErrorListener implements ErrorEventListener {

    @Override
    public void onError(AtomicIOSession session, Throwable cause) {
        log.error("会话 " + (session != null ? session.getId() : "N/A") + " 发生错误: " + cause.getMessage());
    }
}
