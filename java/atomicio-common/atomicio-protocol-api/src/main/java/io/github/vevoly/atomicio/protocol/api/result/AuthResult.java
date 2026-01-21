package io.github.vevoly.atomicio.protocol.api.result;

/**
 * 认证结果对象
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
