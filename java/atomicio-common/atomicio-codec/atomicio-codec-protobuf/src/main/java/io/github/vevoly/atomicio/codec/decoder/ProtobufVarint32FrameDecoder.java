package io.github.vevoly.atomicio.codec.decoder;

import io.github.vevoly.atomicio.codec.utils.ProtobufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Protobuf 消息长度检查处理器
 * 该类用于解决原始 ProtobufVarint32FrameDecoder 无法限制最大包大小的问题。
 * 通过读取包头部的 Base128 Varint 整数来确定后续消息体的长度。
 *
 * @since 0.5.5
 * @author vevoly
 */
@AllArgsConstructor
public class ProtobufVarint32FrameDecoder extends ByteToMessageDecoder {

    private final int maxFrameLength;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 1. 标记当前读取位置，以便数据不足时回滚
        in.markReaderIndex();
        int preIndex = in.readerIndex();
        // 2. 读取 Varint32 长度字段
        int length = ProtobufUtils.readVarint32(in);
        // 3. 如果 readerIndex 没变，说明缓冲区里的数据连长度头都不够（半包），直接返回等待更多数据
        if (preIndex == in.readerIndex()) {
            return;
        }
        // 4. 长度不能为负数
        if (length < 0) {
            throw new CorruptedFrameException("negative length: " + length);
        }
        // 5. 防止超大包攻击
        if (length > maxFrameLength) {
            // 发现大包，跳过所有当前可读数据，防止影响后续通信
            in.skipBytes(in.readableBytes());
            throw new TooLongFrameException("Frame length (" + length + ") exceeds configured max length: " + maxFrameLength);
        }
        // 6. 检查缓冲区数据是否完整
        if (in.readableBytes() < length) {
            // 数据不够（半包），重置指针到 mark 位置，下次数据到来再重试
            in.resetReaderIndex();
            return;
        }
        // 7. 读取完整的消息内容并传递下去, readRetainedSlice 会增加引用计数，确保 ByteBuf 不会被 Netty GC 回收
        out.add(in.readRetainedSlice(length));

    }
}
