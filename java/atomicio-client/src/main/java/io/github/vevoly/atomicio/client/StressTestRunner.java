package io.github.vevoly.atomicio.client;

import io.github.vevoly.atomicio.codec.protobuf.proto.GenericMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 压力测试运行器
 *
 * @since 0.4.1
 * @author vevoly
 */
public class StressTestRunner {

    public static void main(String[] args) throws Exception {
        int concurrentClients = 100; // 并发用户数
        int requestsPerClient = 1000; // 每个用户发送的请求数

        List<TestClient> clients = new ArrayList<>();

        System.out.println("Initializing " + concurrentClients + " clients...");
        for (int i = 0; i < concurrentClients; i++) {
            TestClient client = new TestClient("localhost", 8308);
            client.start();
            clients.add(client);
        }

        ExecutorService executor = Executors.newFixedThreadPool(concurrentClients);
        long startTime = System.currentTimeMillis();

        System.out.println("Starting stress test...");
        for (TestClient client : clients) {
            executor.submit(() -> {
                for (int i = 0; i < requestsPerClient; i++) {
                    // 构造要发送的消息
                    GenericMessage message = buildSomeMessage(i);
                    client.sendMessage(message);
                    // 可以在这里加入短暂的 sleep 来控制发送速率 (QPS)
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        long totalRequests = (long) concurrentClients * requestsPerClient;
        double tps = (double) totalRequests / duration * 1000;

        System.out.println("=========================================");
        System.out.println("Stress Test Finished");
        System.out.println("Total Time: " + duration + " ms");
        System.out.println("Total Requests: " + totalRequests);
        System.out.println("Throughput (TPS): " + String.format("%.2f", tps));
        System.out.println("=========================================");
    }

    private static GenericMessage buildSomeMessage(int i) {
        // ... 构造一个 Protobuf 消息
        return GenericMessage.getDefaultInstance();
    }
}