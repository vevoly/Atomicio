package io.github.vevoly.atomicio.client.core.internal;

import io.github.vevoly.atomicio.protocol.api.message.AtomicIOMessage;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内部请求管理器
 * 负责处理异步请求的 sequenceId 生成和响应匹配。
 *
 * @since 0.6.6
 * @author vevoly
 */
public class AtomicIOClientRequestManager {

    /**
     * 用于生成全局唯一的、线程安全的序列ID。
     */
    private final AtomicLong sequenceIdGenerator = new AtomicLong(0);

    /**
     * 存储所有飞行中的请求。
     * Key: sequenceId
     * Value: 一个 Future，当收到对应 sequenceId 的响应时，这个 Future 会被完成。
     */
    private final Map<Long, CompletableFuture<AtomicIOMessage>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * 生成下一个可用的 sequenceId。
     *
     * @return a new unique sequenceId.
     */
    public long nextSequenceId() {
        return sequenceIdGenerator.incrementAndGet();
    }

    /**
     * 为一个新的请求注册一个 Future，并返回该 Future 以便调用者可以等待结果。
     *
     * @param sequenceId 要注册的请求的 sequenceId。
     * @return 一个新的 CompletableFuture，它将由相应 sequenceId 的响应来完成。
     */
    public CompletableFuture<AtomicIOMessage> registerRequest(long sequenceId) {
        CompletableFuture<AtomicIOMessage> future = new CompletableFuture<>();

        // 增加超时处理，防止内存泄漏
        future.orTimeout(10, TimeUnit.SECONDS); // 10秒超时，可配置

        pendingRequests.put(sequenceId, future);
        return future;
    }

    /**
     * 当收到服务器的消息时，调用此方法来完成一个挂起的请求。
     *
     * @param sequenceId 收到消息的 sequenceId。
     * @param response   收到的响应消息对象。
     * @return 如果找到了一个匹配的挂起请求并成功完成它，则返回 true；
     *         否则返回 false（意味着这可能是一个服务器主动推送的消息）。
     */
    public boolean completeRequest(long sequenceId, AtomicIOMessage response) {
        CompletableFuture<AtomicIOMessage> future = pendingRequests.remove(sequenceId);
        if (future != null) {
            future.complete(response);
            return true;
        }
        return false;
    }

    /**
     * 当客户端断开连接时，调用此方法来清理所有挂起的请求。
     *
     * @param cause 断开连接的原因
     */
    public void clear(Throwable cause) {
        // 遍历所有挂起的请求，并以异常方式完成它们
        pendingRequests.forEach((seqId, future) -> {
            future.completeExceptionally(cause);
        });
        pendingRequests.clear();
    }
}
