package com.github.ehdez73.code2req.infrastructure.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionConfig;
import com.github.ehdez73.code2req.extraction.domain.model.QuarantineConfig;
import com.github.ehdez73.code2req.common.domain.OutputConfig;
import com.github.ehdez73.code2req.infrastructure.snapshot.RefreshableDataSource;
import com.zaxxer.hikari.HikariDataSource;

import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties({ExecutionConfig.class, QuarantineConfig.class, OutputConfig.class})
public class AppConfig {

    Logger logger = org.slf4j.LoggerFactory.getLogger(AppConfig.class);

    @Bean
    @Primary
    public RefreshableDataSource dataSource(
            @Value("${spring.datasource.url}") String url) {
        logger.info("Using SQLite database URL: {}", url);
        var hds = new HikariDataSource();
        hds.setJdbcUrl(url);
        hds.setDriverClassName("org.sqlite.JDBC");
        hds.setMaximumPoolSize(10);
        hds.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000; PRAGMA synchronous=NORMAL;");
        return new RefreshableDataSource(hds);
    }

    @Bean("orchestratorTaskExecutor")
    public Executor orchestratorTaskExecutor(ExecutionConfig executionConfig) {
        int corePoolSize = executionConfig.maxConcurrentLlmCalls() != null
            ? executionConfig.maxConcurrentLlmCalls() : 5;
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(Math.max(10, corePoolSize));
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("c2r-orchestrator-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer finishReasonCustomizer() {
        Module module = new SimpleModule().addDeserializer(OpenAiApi.ChatCompletionFinishReason.class,
                new JsonDeserializer<>() {
                    @Override
                    public OpenAiApi.ChatCompletionFinishReason deserialize(JsonParser p,
                            DeserializationContext ctxt) throws IOException {
                        String value = p.getValueAsString();
                        for (var r : OpenAiApi.ChatCompletionFinishReason.values()) {
                            if (r.name().equalsIgnoreCase(value))
                                return r;
                        }
                        return OpenAiApi.ChatCompletionFinishReason.UNKNOWN;
                    }
                });
        return builder -> builder.modules(module);
    }

    @Bean
    public OpenAiApi openAiApi(
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.api-key}") String apiKey
    ) {
        logger.info("Using OpenAI API base URL: {}", baseUrl);
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }

    @Bean
    public OpenAiChatModel openAiChatModel(
            OpenAiApi openAiApi,
            @Value("${spring.ai.openai.chat.options.model}") String model
    ) {
        logger.info("Using OpenAI chat model: {}", model);
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .build();
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }
}
