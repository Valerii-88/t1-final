package ru.t1.limitservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class LimitServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LimitServiceApplication.class, args);
    }
}
