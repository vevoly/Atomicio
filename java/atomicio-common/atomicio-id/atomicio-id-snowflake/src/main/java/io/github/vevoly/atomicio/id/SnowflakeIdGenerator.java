package io.github.vevoly.atomicio.id;

import io.github.vevoly.atomicio.common.api.exception.IdGenerationException;
import io.github.vevoly.atomicio.common.api.id.AtomicIOIdGenerator;

/**
 * 分布式 ID 生成器：雪花算法 (Snowflake) 的标准实现
 * 分布式 ID 算法
 * ID 结构构成：
 * 1 位 (符号位) | 41 位 (毫秒时间戳) | 5 位 (数据中心 ID) | 5 位 (工作机器 ID)
 *
 * @since 0.6.1
 * @author vevoly
 */
public class SnowflakeIdGenerator implements AtomicIOIdGenerator {

    // --- 算法结构相关常量 ---

    // 起始时间戳 (Epoch)，例如：2024-01-01T00:00:00Z
    private final long epoch = 1704067200000L;

    private final long workerIdBits = 5L;
    private final long datacenterIdBits = 5L;
    // 最大支持的工作机器 ID (31)
    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
    // 最大支持的数据中心 ID (31)
    private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);

    private final long sequenceBits = 12L;
    private final long workerIdShift = sequenceBits;
    private final long datacenterIdShift = sequenceBits + workerIdBits;
    private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
    // 序列号掩码 (4095)，用于保证序列号在 0-4095 之间循环
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);

    // --- 实例状态变量 ---

    private final long workerId;
    private final long datacenterId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /**
     * 创建一个新的雪花算法 ID 生成器。
     *
     * @param workerId     工作机器 ID (0-31)。在同一个数据中心内必须唯一。
     * @param datacenterId 数据中心 ID (0-31)。
     */
    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(String.format("工作机器 ID 不能大于 %d 或小于 0", maxWorkerId));
        }
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("数据中心 ID 不能大于 %d 或小于 0", maxDatacenterId));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * 生成下一个唯一 ID。
     * 该方法是同步的 (synchronized)，以确保单实例在多线程环境下的线程安全。
     *
     * @return 下一个唯一 ID。
     * @throws IdGenerationException 如果系统时钟被回拨，则抛出异常。
     */
    @Override
    public synchronized long nextId() throws IdGenerationException {
        long timestamp = timeGen();

        // **检测时钟回拨**
        if (timestamp < lastTimestamp) {
            throw new IdGenerationException(
                    String.format("检测到时钟回拨。拒绝为回拨后的 %d 毫秒生成 ID", lastTimestamp - timestamp)
            );
        }

        // 如果在同一毫秒内，则增加序列号
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            // 序列号溢出，必须等待进入下一个毫秒
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 进入新的毫秒，重置序列号为 0
            sequence = 0L;
        }

        // 更新最后一次生成 ID 的时间戳
        lastTimestamp = timestamp;

        // **将各部分组合成一个 64 位的 long 类型 ID**
        return ((timestamp - epoch) << timestampLeftShift) |
                (datacenterId << datacenterIdShift) |
                (workerId << workerIdShift) |
                sequence;
    }

    /**
     * 阻塞当前线程，直到进入下一个毫秒。
     * @param lastTimestamp 最后一次处理的时间戳。
     * @return 新的、更大的时间戳。
     */
    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * 获取系统当前毫秒时间戳。
     * @return 当前时间戳。
     */
    protected long timeGen() {
        return System.currentTimeMillis();
    }
}
