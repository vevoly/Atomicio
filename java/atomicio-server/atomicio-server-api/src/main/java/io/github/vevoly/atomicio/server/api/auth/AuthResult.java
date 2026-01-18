package io.github.vevoly.atomicio.server.api.auth;

/**
 * 认证结果
 *
 * @since 0.6.5
 * @author vevoly
 */
public record AuthResult (boolean success, String userId, String deviceId, String errorMessage) {
    public static AuthResult success(String userId, String deviceId) {
        return new AuthResult(true, userId, deviceId, null);
    }

    public static AuthResult failure(String message) {
        return new AuthResult(false, null, null, message);
    }

}
