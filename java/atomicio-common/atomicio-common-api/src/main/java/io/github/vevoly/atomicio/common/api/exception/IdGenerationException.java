package io.github.vevoly.atomicio.common.api.exception;

/**
 * ID 生成异常
 *
 * @since 0.6.1
 * @author vevoly
 */
public class IdGenerationException extends RuntimeException {

    public IdGenerationException(String message) {
        super(message);
    }

    public IdGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
