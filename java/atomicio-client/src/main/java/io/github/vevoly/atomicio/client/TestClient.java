package io.github.vevoly.atomicio.client;

import io.github.vevoly.atomicio.codec.protobuf.proto.GenericMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import lombok.extern.slf4j.Slf4j;

/**
 * 测试客户端
 * protobuf 协议不能使用 nc 进行测试
 *
 * @since 0.4.1
 * @author vevoly
 */
@Slf4j
public class TestClient {

    private final String host;
    private final int port;
    private Channel channel;

    public TestClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            // Outbound Handlers (发送时，从下到上)
                            // 长度前置器：在二进制消息前加上 Varint32 长度字段
                            p.addLast(new ProtobufVarint32LengthFieldPrepender());
                            // Protobuf 编码器：将 Protobuf 对象序列化为字节
                            p.addLast(new ProtobufEncoder());
                            // Inbound Handlers (接收时，从上到下)
                            // 帧解码器：根据长度字段切分消息包
                            p.addLast(new ProtobufVarint32FrameDecoder());
                            // Protobuf 解码器：将字节反序列化为 Protobuf 对象
                            p.addLast(new ProtobufDecoder(GenericMessage.getDefaultInstance()));
                            // 客户端的业务处理器
                            p.addLast(new ClientHandler());
                        }
                    });

            // 启动客户端
            ChannelFuture f = b.connect(host, port).sync();
            this.channel = f.channel();
            log.info("Client connected to {}:{}", host, port);

            // 等待连接关闭
            // f.channel().closeFuture().sync();
        } finally {
            // group.shutdownGracefully();
        }
    }

    public void sendMessage(GenericMessage message) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message);
        } else {
            log.error("Channel is not active. Cannot send message.");
        }
    }

    // 客户端的业务处理器
    @Slf4j
    private static class ClientHandler extends SimpleChannelInboundHandler<GenericMessage> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, GenericMessage msg) throws Exception {
            log.info("Received from server: [commandId={}]", msg.getCommandId());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("Exception caught in client handler", cause);
            ctx.close();
        }
    }
}
