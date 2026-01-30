package io.github.vevoly.atomicio.common.api.config;

/**
 * 设备踢出策略类型
 * 当达到上限时，踢掉哪个设备
 *
 * @since 0.6.10
 * @author vevoly
 */
public enum KickDeviceStrategyType {

    /**
     * 踢掉登录时间最早的
     */
    KICK_OLDEST_LOGIN,

    /**
     * 踢掉最久未活跃的 - 更复杂，需要记录心跳
     */
    KICK_LEAST_RECENTLY_USED,

    ;
}
