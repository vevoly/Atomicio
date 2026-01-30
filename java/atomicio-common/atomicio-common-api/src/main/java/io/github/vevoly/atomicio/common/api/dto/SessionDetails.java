package io.github.vevoly.atomicio.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 封装了一个已注册会话的详细元数据。
 * 用于登录策略决策。
 *
 * @since 0.6.10
 * @author vevoly
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionDetails {

    /**
     * 该会话所在的节点ID。
     */
    private String nodeId;

    /**
     * 该会话的设备类型 (e.g., "PC", "iOS")。
     */
    private String deviceType;

    /**
     * 该会话的登录时间戳。
     */
    private long loginTime;

    /**
     * 该会话最后一次活跃的时间戳 (用于 LRU 策略)。
     */
    private long lastActivityTime;

}
