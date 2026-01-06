package io.github.vevoly.atomicio.example.simple.service;

/**
 * 模拟认证
 */
public class AuthService {
    public static boolean verify(String token) {
        return token != null && !token.isEmpty();
    }
}
