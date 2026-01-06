package io.github.vevoly.atomicio.core.listener;

import io.github.vevoly.atomicio.api.AtomicIOEngine;
import io.github.vevoly.atomicio.api.config.AtomicIOProperties;
import io.github.vevoly.atomicio.api.listeners.EngineReadyListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 一个私有的内部类，专门负责打印欢迎横幅。
 *
 * @since 0.1.5
 * @author vevoly
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WelcomeBannerPrinter implements EngineReadyListener {

    @Autowired
    private AtomicIOProperties properties;

    // ANSI 颜色代码
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BRIGHT_GREEN = "\u001B[92m";
    private static final String ANSI_BRIGHT_CYAN = "\u001B[96m";

    @Override
    public void onEngineReady(AtomicIOEngine engine) {
        String version = getVersion(); // 获取版本号
        String separator = System.lineSeparator();

        String banner = String.join(separator,
                "", // 开头空行
                ANSI_BRIGHT_GREEN, // --- 开始绿色 ---
                        "_____/\\\\\\\\\\\\\\\\\\_____/\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\_______/\\\\\\\\\\_______/\\\\\\\\____________/\\\\\\\\__/\\\\\\\\\\\\\\\\\\\\\\________/\\\\\\\\\\\\\\\\\\__/\\\\\\\\\\\\\\\\\\\\\\_______/\\\\\\\\\\______\n" +
                        " ___/\\\\\\\\\\\\\\\\\\\\\\\\\\__\\///////\\\\\\/////______/\\\\\\///\\\\\\____\\/\\\\\\\\\\\\________/\\\\\\\\\\\\_\\/////\\\\\\///______/\\\\\\////////__\\/////\\\\\\///______/\\\\\\///\\\\\\____\n" +
                        "  __/\\\\\\/////////\\\\\\_______\\/\\\\\\_________/\\\\\\/__\\///\\\\\\__\\/\\\\\\//\\\\\\____/\\\\\\//\\\\\\_____\\/\\\\\\_______/\\\\\\/_______________\\/\\\\\\_______/\\\\\\/__\\///\\\\\\__\n" +
                        "   _\\/\\\\\\_______\\/\\\\\\_______\\/\\\\\\________/\\\\\\______\\//\\\\\\_\\/\\\\\\\\///\\\\\\/\\\\\\/_\\/\\\\\\_____\\/\\\\\\______/\\\\\\_________________\\/\\\\\\______/\\\\\\______\\//\\\\\\_\n" +
                        "    _\\/\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\_______\\/\\\\\\_______\\/\\\\\\_______\\/\\\\\\_\\/\\\\\\__\\///\\\\\\/___\\/\\\\\\_____\\/\\\\\\_____\\/\\\\\\_________________\\/\\\\\\_____\\/\\\\\\_______\\/\\\\\\_\n" +
                        "     _\\/\\\\\\/////////\\\\\\_______\\/\\\\\\_______\\//\\\\\\______/\\\\\\__\\/\\\\\\____\\///_____\\/\\\\\\_____\\/\\\\\\_____\\//\\\\\\________________\\/\\\\\\_____\\//\\\\\\______/\\\\\\__\n" +
                        "      _\\/\\\\\\_______\\/\\\\\\_______\\/\\\\\\________\\///\\\\\\__/\\\\\\____\\/\\\\\\_____________\\/\\\\\\_____\\/\\\\\\______\\///\\\\\\______________\\/\\\\\\______\\///\\\\\\__/\\\\\\____\n" +
                        "       _\\/\\\\\\_______\\/\\\\\\_______\\/\\\\\\__________\\///\\\\\\\\\\/_____\\/\\\\\\_____________\\/\\\\\\__/\\\\\\\\\\\\\\\\\\\\\\____\\////\\\\\\\\\\\\\\\\\\__/\\\\\\\\\\\\\\\\\\\\\\____\\///\\\\\\\\\\/_____\n" +
                        "        _\\///________\\///________\\///_____________\\/////_______\\///______________\\///__\\///////////________\\/////////__\\///////////_______\\/////_______" +
                ANSI_RESET, // --- 重置颜色 ---
                "",
                ANSI_BRIGHT_CYAN + "  :: Atomicio IO Engine :: (v" + version + ") ::  Author: Vevoly " + ANSI_RESET,
                "=================================================================================================",
                "  Port         : " + properties.getPort(),
                "  Cluster Mode : " + (properties.getCluster().isEnabled() ? "Enabled (" + properties.getCluster().getType() + ")" : "Disabled"),
                "  Engine is running and ready for connections!",
                "=================================================================================================",
                ""
        );

        System.out.println(banner);
    }

    private static String getVersion() {
        // todo 从 MANIFEST.MF 文件中读取版本号，这是一个更专业的做法
        // 这里先用一个占位符
        return "1.0.0";
    }
}

