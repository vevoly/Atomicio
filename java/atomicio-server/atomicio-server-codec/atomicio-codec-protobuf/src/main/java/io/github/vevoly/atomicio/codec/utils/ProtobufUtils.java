package io.github.vevoly.atomicio.codec.utils;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;

/**
 * Protobuf 工具类
 *
 * @since 0.5.5
 * @author vevoly
 */
public class ProtobufUtils {

    private ProtobufUtils() {}

    /**
     * 读取 Base128 变长整数 (Varint32)。
     * 逻辑参考 Google Protobuf 的 CodedInputStream。
     */
    public static int readVarint32(ByteBuf in) {
        if (!in.isReadable()) {
            return 0;
        }
        in.markReaderIndex();
        byte tmp = in.readByte();
        if (tmp >= 0) {
            return tmp;
        } else {
            int result = tmp & 127;
            if (!in.isReadable()) {
                in.resetReaderIndex();
                return 0;
            }
            if ((tmp = in.readByte()) >= 0) {
                result |= tmp << 7;
            } else {
                result |= (tmp & 127) << 7;
                if (!in.isReadable()) {
                    in.resetReaderIndex();
                    return 0;
                }
                if ((tmp = in.readByte()) >= 0) {
                    result |= tmp << 14;
                } else {
                    result |= (tmp & 127) << 14;
                    if (!in.isReadable()) {
                        in.resetReaderIndex();
                        return 0;
                    }
                    if ((tmp = in.readByte()) >= 0) {
                        result |= tmp << 21;
                    } else {
                        result |= (tmp & 127) << 21;
                        if (!in.isReadable()) {
                            in.resetReaderIndex();
                            return 0;
                        }
                        result |= (tmp = in.readByte()) << 28;
                        if (tmp < 0) {
                            // 丢弃 5 字节以上的多余字节，或者判定为损坏的数据
                            throw new CorruptedFrameException("Malformed varint32: too many bytes");
                        }
                    }
                }
            }
            return result;
        }
    }
}
