package io.github.vevoly.atomicio.common.api.id;

import java.util.Arrays;

/**
 * ID 生成器类型
 *
 * @since 0.6.1
 * @author vevoly
 */
public enum AtomicIOIdGeneratorType {
    SNOWFLAKE,  // 雪花算法
    UUID,       // UUID
    CUSTOM,     // 用户自定义
    UNKNOWN;    // 未知

    /**
     * 从字符串安全地转换为 AtomicIOIdGeneratorType 枚举实例。
     * 这个方法大小写不敏感
     *
     * @param typeString 用户在配置文件中输入的类型字符串
     * @return 对应的枚举实例，如果找不到则返回 UNKNOWN
     */
    public static AtomicIOIdGeneratorType fromString(String typeString) {
        if (typeString == null || typeString.trim().isEmpty()) {
            return UNKNOWN;
        }

        // 忽略大小写进行匹配
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(typeString))
                .findFirst()
                .orElse(UNKNOWN); // 如果没找到，返回 UNKNOWN
    }
}
