package io.github.vevoly.atomicio.protocol.api.result;


/**
 * 通用的操作结果对象，用于表示简单的成功/失败响应
 *
 * @since 0.6.6
 * @author vevoly
 */
public record GeneralResult(boolean success, String message) {

    public static GeneralResult success(String message) {
        return new GeneralResult(true, message);
    }

    public static GeneralResult failure(String message) {
        return new GeneralResult(false, message);
    }
}
