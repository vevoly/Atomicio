package io.github.vevoly.atomicio.common.api.config;

/**
 * 登录策略类型
 *
 * @since 0.6.10
 * @author vevoly
 */
public enum LoginStrategyType {

    /**
     * 全局单点登录
     * 定义: 一个 User 在任何时间、任何地点，只能有一个活跃的 Device Instance。
     * 行为: 当 userA 在 iPhone 上登录后，如果他再尝试用同一个账号 userA 在 MacBook 上登录，iPhone 会被立即踢下线。
     * 例子: 大部分网络游戏（如《英雄联盟》、《魔兽世界》）、银行App。
     */
    SINGLE_ANY_DEVICE,

    /**
     * 按设备类型单点登录
     * 定义: 一个 User 可以同时在不同类型的设备上登录，但对于同一种类型的设备，只能有一个活跃实例。
     * 行为:
     *  userA 在 iPhone (iOS) 上登录。OK。
     *  userA 再在 MacBook (PC) 上登录。OK，iPhone 不会被踢下线。两者同时在线。
     *  userA 再在另一台电脑的微信 (PC) 上登录。冲突发生！ MacBook 上的微信会被踢下线。
     * 例子: 微信、QQ、Telegram 等绝大多数现代 IM 应用。它们允许你手机和电脑同时在线，但不允许两个手机或两台电脑同时登录同一个账号。
     */
    SINGLE_PER_DEVICE_TYPE,

    /**
     * 全局多点登录
     * 定义: 一个 User 可以同时在任意数量、任意类型的设备上登录。
     * 行为: 没有任何限制。userA 可以在 2 个 iPhone 和 3 个 PC 上同时登录，它们都互相不影响。
     * 例子: Discord, Slack (在一定程度上)。
     */
    MULTI_DEVICE,

    /**
     * 有限多点登录
     * 定义: 一个 User 最多只能同时在 N 个设备上登录。
     * 行为: 当 userA 尝试在第 N+1 个设备上登录时，会发生冲突。此时，根据具体策略，可能会：
     *  踢掉最早登录的设备 (LRU - Least Recently Used)。
     *  拒绝新设备的登录。
     * 例子: 一些流媒体服务（如 Netflix），限制一个账号最多在 4 个设备上同时观看。
     */
    LIMITED_MULTI_DEVICE,
    ;
}
