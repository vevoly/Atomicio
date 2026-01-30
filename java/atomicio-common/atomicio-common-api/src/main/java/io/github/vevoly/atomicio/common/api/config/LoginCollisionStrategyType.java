package io.github.vevoly.atomicio.common.api.config;

/**
 * 登录冲突策略类型
 *
 * @since 0.6.10
 * @author vevoly
 */
public enum LoginCollisionStrategyType {

    /**
     * 踢掉旧设备，让新设备上线
     */
    KICK_OLD,

    /**
     * 拒绝新设备，保持旧设备在线
     */
    REJECT_NEW,

    ;
}
