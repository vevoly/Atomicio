package io.github.vevoly.atomicio.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class AtomicIOExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtomicIOExampleApplication.class, args);
        log.info("=====================================================");
        log.info(" 欢迎使用 Atomicio IO 引擎!");
        log.info(" 所有监听器已经注册完成，现在可以尝试连接！");
        log.info("=====================================================");
    }
}
