package com.github.ehdez73.code2req.config;

import com.github.ehdez73.code2req.model.ExecutionConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public ExecutionConfig executionConfig() {
        return ExecutionConfig.defaultConfig();
    }
}
