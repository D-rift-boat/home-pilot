package com.dboat.iot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@Slf4j
@SpringBootApplication
public class HomePilotApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(HomePilotApplication.class, args);

        log.info("========================================");
        log.info("\uD83C\uDF89 Home‑Pilot service started successfully");
        log.info("✅ Total beans: {}", context.getBeanDefinitionCount());
        log.info("========================================");
    }
}
