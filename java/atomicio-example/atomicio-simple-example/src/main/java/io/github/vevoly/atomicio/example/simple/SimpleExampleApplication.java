package io.github.vevoly.atomicio.example.simple;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class SimpleExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleExampleApplication.class, args);
    }
}
