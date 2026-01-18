package io.github.vevoly.atomicio.example.simple.service;

/**
 * 模拟认证
 */
public class AuthService {
    public static boolean verify(String userId, String token) {
        return "tok".equals(token);
    }
}
