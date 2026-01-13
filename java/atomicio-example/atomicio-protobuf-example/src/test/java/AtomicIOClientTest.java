import io.github.vevoly.atomicio.server.api.AtomicIOCodecProvider;
import io.github.vevoly.atomicio.client.api.AtomicIOClient;
import io.github.vevoly.atomicio.client.api.config.AtomicIOClientConfig;
import io.github.vevoly.atomicio.client.core.DefaultAtomicIOClient;
import io.github.vevoly.atomicio.codec.ProtobufCodecProvider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AtomicIOClientTest {
    public static void main(String[] args) {
        AtomicIOClientConfig config = new AtomicIOClientConfig();
        AtomicIOCodecProvider codecProvider = new ProtobufCodecProvider();
        AtomicIOClient atomicIOClient = new DefaultAtomicIOClient(config, codecProvider);

        atomicIOClient.connect();
    }
    void testClientConnect() {

        AtomicIOClientConfig config = new AtomicIOClientConfig();
        AtomicIOCodecProvider codecProvider = new ProtobufCodecProvider();
        AtomicIOClient atomicIOClient = new DefaultAtomicIOClient(config, codecProvider);

        atomicIOClient.connect();

        // 登录
        String userId = "0001";
        String token = "token-0001";
//        LoginRequest loginRequest = new LoginRequest();
    }
}
