package io.github.vevoly.atomicio.api.constants;

/**
 * 空闲状态枚举
 */
public enum IdleState {
    READER_IDLE, // 读空闲 (长时间没有收到客户端数据)
    WRITER_IDLE, // 写空闲 (长时间没有向客户端写数据)
    ALL_IDLE     // 读写都空闲
}
