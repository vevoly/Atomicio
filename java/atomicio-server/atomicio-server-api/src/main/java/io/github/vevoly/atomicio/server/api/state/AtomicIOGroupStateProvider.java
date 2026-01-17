package io.github.vevoly.atomicio.server.api.state;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 群组状态提供器
 * 定义所有与“群组”状态相关的读写操作
 *
 * @since 0.6.4
 * @author vevoly
 */
public interface AtomicIOGroupStateProvider {

    /**
     * 将一个用户加入一个群组。
     * 这是一个幂等操作。
     *
     * @param groupId 群组ID
     * @param userId  用户ID
     * @return a Future indicating completion.
     */
    CompletableFuture<Void> join(String groupId, String userId);

    /**
     * 将一个用户从一个群组中移除。
     *
     * @param groupId 群组ID
     * @param userId  用户ID
     * @return a Future indicating completion.
     */
    CompletableFuture<Void> leave(String groupId, String userId);

    /**
     * 获取一个群组的所有成员的 UserID。
     *
     * @param groupId 群组ID
     * @return a Future of a Set of user IDs.
     */
    CompletableFuture<Set<String>> getGroupMembers(String groupId);

    /**
     * 获取一个群组的成员总数。
     *
     * @param groupId 群组ID
     * @return a Future of a long representing the total number of members in the group.
     */
    CompletableFuture<Long> getGroupMemberCount(String groupId);

    /**
     * 检查一个用户是否是某个群组的成员。
     * 这是一个比 getGroupMembers().contains(...) 高效得多的操作。
     *
     * @param groupId 群组ID
     * @param userId  要检查的用户ID
     * @return a Future of a boolean.
     */
    CompletableFuture<Boolean> isGroupMember(String groupId, String userId);

    /**
     * 获取一个用户所加入的所有群组的 ID 列表。
     * 在 Redis 中，维护一个反向索引来实现 (e.g., a Set user_groups:{userId})。
     *
     * @param userId 用户ID
     * @return a Future of a Set of group IDs.
     */
    CompletableFuture<Set<String>> getGroupsForUser(String userId);

    /**
     * 获取一个用户所加入的群组的总数量。
     *
     * @param userId 用户ID
     * @return a Future of a long.
     */
    CompletableFuture<Long> getGroupCountForUser(String userId);
}
