package com.github.ehdez73.code2req;

import com.embabel.agent.config.annotation.EnableAgents;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableAgents
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
