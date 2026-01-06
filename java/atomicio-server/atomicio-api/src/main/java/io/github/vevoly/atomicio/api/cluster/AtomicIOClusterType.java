package io.github.vevoly.atomicio.api.cluster;

import java.util.Arrays;

/**
 * 集群类型
 *
 * @since 0.0.6
 * @author vevoly
 */
public enum AtomicIOClusterType {
    REDIS,
    ROCKETMQ,
    RABBITMQ,
    KAFKA,
    UNKNOWN;

    /**
     * 从字符串安全地转换为 AtomicIOClusterType 枚举实例。
     * 这个方法大小写不敏感
     *
     * @param typeString 用户在配置文件中输入的类型字符串
     * @return 对应的枚举实例，如果找不到则返回 UNKNOWN
     */
    public static AtomicIOClusterType fromString(String typeString) {
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
