package io.github.vevoly.atomicio.client;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.github.vevoly.atomicio.api.AtomicIOCommand;
import io.github.vevoly.atomicio.codec.protobuf.proto.GenericMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户端启动器
 *
 * @since 0.4.1
 * @author vevoly
 */
@Slf4j
public class SingleClientRunner {

    public static void main(String[] args) throws Exception {
        TestClient client = new TestClient("localhost", 8308); // 确保端口与服务器一致
        client.start();

        String loginPayload = "testUser001:some-secret-token";
        ByteString bytes = ByteString.copyFromUtf8(loginPayload);
        GenericMessage message = GenericMessage.newBuilder()
                .setCommandId(AtomicIOCommand.LOGIN)
                .setPayload(bytes)
                .build();

        // 3. 发送消息
        log.info("Sending login request...");
        client.sendMessage(message);

        // 保持运行一段时间以接收服务器响应
        Thread.sleep(5000);
    }
}
