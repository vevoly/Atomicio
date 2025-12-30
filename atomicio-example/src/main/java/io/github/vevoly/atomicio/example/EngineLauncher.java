package io.github.vevoly.atomicio.example;

import io.github.vevoly.atomicio.api.AtomicIOEngine;
import io.github.vevoly.atomicio.api.AtomicIOEventType;
import io.github.vevoly.atomicio.core.engine.DefaultAtomicIOEngine;

public class EngineLauncher {
    public static void main(String[] args) {
        // 1. 创建引擎实例
        AtomicIOEngine engine = new DefaultAtomicIOEngine(8888);
        // 2. 注册监听器
        engine.on(AtomicIOEventType.CONNECT, session -> {
            System.out.println("新连接建立: " + session.getId());
        });
        engine.on(AtomicIOEventType.DISCONNECT, session -> {
            System.out.println("连接断开: " + session.getId());
        });
        engine.onMessage(((session, message) -> {
            System.out.println("收到消息: " + new String(message.getPayload()));
            session.send(message);
        }));
        engine.onError(((session, cause) -> {
            System.err.println("会话 " + (session != null ? session.getId() : "N/A") + " 发生错误: " + cause.getMessage());
        }));

        // 3. 启动引擎
        System.out.println("Atomicio Engine 启动中...");
        engine.start();
    }
}
