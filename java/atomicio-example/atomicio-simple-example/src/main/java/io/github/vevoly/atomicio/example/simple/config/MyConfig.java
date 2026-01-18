package io.github.vevoly.atomicio.example.simple.config;

import io.github.vevoly.atomicio.example.simple.auth.MyAuthenticator;
import io.github.vevoly.atomicio.server.api.auth.Authenticator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
//public class MyConfig {
//
//    /**
//     * 向 Spring 容器提供自定义的认证器实现。
//     * AtomicIO 框架的自动配置会自动发现并使用这个 Bean。
//     */
//    @Bean
//    public Authenticator authenticator() {
//        return new MyAuthenticator();
//    }
//}
