package io.github.vevoly.atomicio.client.api.config;

import io.github.vevoly.atomicio.client.api.AtomicIOClient;
import io.github.vevoly.atomicio.client.api.constants.AtomicIOClientConstant;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * AtomicIO 客户端的配置类。
 * 支持链式调用进行设置。
 *
 * @since 0.5.0
 * @author vevoly
 */
@Data
@Accessors(chain = true)
public class AtomicIOClientConfig {

    /**
     * 服务器主机地址。
     */
    private String host = AtomicIOClientConstant.DEFAULT_HOST;

    /**
     * 服务器端口。
     */
    private int port = AtomicIOClientConstant.DEFAULT_PORT;

    /**
     * 连接超时时间（毫秒）。
     */
    private int connectTimeoutMillis = AtomicIOClientConstant.DEFAULT_CONNECT_TIMEOUT_MILLIS;

    /**
     * 消息最大长度
     * 一定要与服务器端保持一致，否则会出现问题
     */
    private int maxFrameLength = AtomicIOClientConstant.DEFAULT_MAX_FRAME_LENGTH;

    // --- 心跳配置 ---
    /**
     * 是否启用客户端主动发送心跳。
     */
    private boolean heartbeatEnabled = AtomicIOClientConstant.DEFAULT_HEARTBEAT_ENABLED;

    /**
     * 写空闲时间（秒）。当客户端在该时间内没有发送任何数据时，会自动发送一个心跳包。
     * 必须大于0才能生效。
     */
    private int writerIdleSeconds = AtomicIOClientConstant.DEFAULT_WRITER_IDLE_SECONDS;

    // --- 重连配置 ---
    /**
     * 是否启用断线自动重连。
     */
    private boolean reconnectEnabled = AtomicIOClientConstant.DEFAULT_RECONNECT_ENABLED;

    /**
     * 初始重连延迟（秒）。
     */
    private int initialReconnectDelaySeconds = AtomicIOClientConstant.DEFAULT_INITIAL_RECONNECT_DELAY_SECONDS;

    /**
     * 最大重连延迟（秒）。重连延迟会以指数形式增长，直到达到此上限。
     */
    private int maxReconnectDelaySeconds = AtomicIOClientConstant.DEFAULT_MAX_RECONNECT_DELAY_SECONDS;



    /**
     * SSL/TLS 配置
     */
    private Ssl ssl = new Ssl();

    @Data
    @Accessors(chain = true)
    public static class Ssl {

        private boolean enabled = false; // 默认 false， 关闭

        private String trustCertPath; // SSL 证书链文件路径

        /**
         * 自动从 classpath 寻找证书文件并设置路径。
         * 支持 IDE 运行环境和 JAR 包打包运行环境。
         *
         * @param resourcePath 资源路径 (例如 "server.crt" 或 "certs/server.crt")
         * @return Ssl 对象本身，支持链式调用
         */
        public Ssl setTrustCertFromResource(String resourcePath) {
            try {
                // 1. 获取资源 URL
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                if (classLoader == null) {
                    classLoader = Ssl.class.getClassLoader();
                }
                URL url = classLoader.getResource(resourcePath);
                if (url == null) {
                    throw new FileNotFoundException("无法在类路径(classpath)下找到证书文件: " + resourcePath);
                }
                // 2. 判断是否在 JAR 包中
                if (url.getProtocol().equals("jar")) {
                    // 如果在 JAR 中，无法直接获取 File 路径，需将其拷贝到临时文件夹
                    File tempFile = File.createTempFile("atomicio-cert-", ".tmp");
                    tempFile.deleteOnExit(); // 程序退出时自动删除

                    try (InputStream is = url.openStream()) {
                        Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    this.trustCertPath = tempFile.getAbsolutePath();
                } else {
                    // 3. 在普通文件系统中 (IDE运行)，处理空格后直接转换
                    this.trustCertPath = new File(url.toURI()).getAbsolutePath();
                }
                this.enabled = true; // 自动开启 SSL 标识
                return this;
            } catch (Exception e) {
                throw new RuntimeException("SDK加载证书资源失败: " + resourcePath, e);
            }
        }
    }

}
