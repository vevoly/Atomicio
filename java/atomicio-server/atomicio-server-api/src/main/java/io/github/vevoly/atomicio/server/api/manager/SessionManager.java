package io.github.vevoly.atomicio.server.api.manager;

import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;
import io.github.vevoly.atomicio.server.api.session.AtomicIOSession;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * 会话管理器接口
 * 负责物理连接
 *
 * @since 0.6.5
 * @author vevoly
 */
public interface SessionManager {

    /**
     * 物理连接入库：将本地 Session 对象存入内存
     */
    void addLocalSession(AtomicIOSession session);

    /**
     * 身份绑定
     * 补全属性
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param deviceId  设备 ID
     */
    void bindLocalSession(String sessionId, String userId, String deviceId);

    /**
     * 根据设备 ID 物理踢人：通常用于处理多端登录冲突
     */
    void removeByDeviceId(String deviceId);

    /**
     * 根据会话 ID 移除并断开物理连接
     */
    void removeLocalSession(String sessionId);

    /**
     * 根据会话 ID 移除 Session，但不断开连接
     * @param sessionId
     */
    void removeLocalSessionOnly(String sessionId);

    /**
     * 获取当前用户在本节点的所有活动 Session
     */
    List<AtomicIOSession> getLocalSessionsByUserId(String userId);

    /**
     * 根据会话 ID 获取 Session
     * @param sessionId 会话 ID
     * @return
     */
    AtomicIOSession getLocalSessionById(String sessionId);

    /**
     * 根据设备 ID 获取 Session
     * @param deviceId  设备 ID
     * @return
     */
    AtomicIOSession getLocalSessionByDeviceId(String deviceId);

    /**
     * 本地推送：仅向连接在本物理机上的指定用户发送消息
     * @return 如果本地找到了该用户的连接并尝试发送，返回 true
     */
    boolean sendToUserLocally(String userId, Object message);

    /**
     * 全局本地广播：向当前节点的所有连接发送消息
     */
    void broadcastLocally(Object message);

    /**
     * 本地踢人
     * @param userId            用户 ID
     * @param kickOutMessage    踢人消息
     */
    void kickOutLocally(String userId, @Nullable AtomicIOMessage kickOutMessage);

    /**
     * 根据设备 ID 踢人
     * @param deviceId          设备 ID
     * @param kickOutMessage    踢人消息
     */
    void kickOutByDeviceId(String deviceId, @Nullable AtomicIOMessage kickOutMessage);

    /**
     * 获取当前节点的总连接数
     */
    int getTotalConnectCount();

}
