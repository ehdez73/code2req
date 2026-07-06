package com.github.ehdez73.code2req.infrastructure.cli.command;

import com.github.ehdez73.code2req.infrastructure.config.ManifestLoader;
import com.github.ehdez73.code2req.infrastructure.config.ManifestValidator;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FloatingLinkStore;
import com.github.ehdez73.code2req.infrastructure.persistence.MetricsStore;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionConfig;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStoreSchema;
import com.github.ehdez73.code2req.infrastructure.persistence.TopicLinkStore;
import com.github.ehdez73.code2req.extraction.ExtractionOrchestrator;
import com.embabel.agent.core.AgentPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ExtractCommandTest {

    @TempDir
    Path tempDir;

    private ExtractCommand command;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("extract-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();

        var taskStore = new TaskStore(jdbc);
        var findingStore = new ExecutionFindingStore(jdbc);
        var metricsStore = new MetricsStore(jdbc);
        var floatingLinkStore = new FloatingLinkStore(jdbc);
        var topicLinkStore = new TopicLinkStore(jdbc);

        var executionConfig = new ExecutionConfig(null, null, null, null, null, null, null, null, null, null, null, null);
        var orchestrator = new ExtractionOrchestrator(
            taskStore, findingStore, floatingLinkStore, topicLinkStore, metricsStore,
            mock(AgentPlatform.class), executionConfig, null);
        var manifestLoader = new ManifestLoader();
        var manifestValidator = new ManifestValidator(manifestLoader);

        command = new ExtractCommand(orchestrator, manifestValidator);
    }

    @Test
    void extractDryRunReturnsZeroCounts() {
        String result = command.extract("project-manifest.yaml", true, false, false);
        assertTrue(result.contains("Flows extracted: 0"));
        assertTrue(result.contains("Extract"));
    }

    @Test
    void extractWithInvalidManifestReturnsError() {
        String result = command.extract("nonexistent.yaml", false, false, false);
        assertTrue(result.contains("Error: Manifest file not found"));
    }

    @Test
    void extractWithForceShowsForceMode() {
        String result = command.extract("project-manifest.yaml", true, true, false);
        assertTrue(result.contains("FORCE"));
        assertTrue(result.contains("Phase 3"));
    }
}
