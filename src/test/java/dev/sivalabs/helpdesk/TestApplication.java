package dev.sivalabs.helpdesk;

import org.springframework.boot.SpringApplication;

public class TestApplication {

    static void main(String[] args) {
        System.setProperty("spring.docker.compose.enabled", "false");
        SpringApplication.from(Application::main)
                .with(TestcontainersConfig.class)
                .run(args);
    }
}
