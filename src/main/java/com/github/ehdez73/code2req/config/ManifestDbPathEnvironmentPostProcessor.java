package com.github.ehdez73.code2req.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ManifestDbPathEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ManifestDbPathEnvironmentPostProcessor.class);
    private static final String MANIFEST_FILE = "project-manifest.yaml";
    private static final String DB_PATH_PROPERTY = "spring.datasource.url";

    private final ObjectMapper mapper;

    public ManifestDbPathEnvironmentPostProcessor() {
        this.mapper = new ObjectMapper(new YAMLFactory());
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path manifestPath = Path.of(MANIFEST_FILE);
        if (!Files.exists(manifestPath)) {
            log.debug("No {} found — using default datasource URL from application.properties", MANIFEST_FILE);
            return;
        }

        try {
            JsonNode root = mapper.readTree(manifestPath.toFile());
            JsonNode output = root.get("output");
            if (output == null || !output.has("db-path")) {
                log.debug("Manifest has no output.db-path — using default datasource URL");
                return;
            }

            String dbPath = output.get("db-path").asText();
            if (dbPath == null || dbPath.isBlank()) {
                log.debug("Manifest output.db-path is blank — using default datasource URL");
                return;
            }

            Path absoluteDbPath = Path.of(dbPath).toAbsolutePath().normalize();
            Files.createDirectories(absoluteDbPath.getParent());

            String jdbcUrl = "jdbc:sqlite:" + absoluteDbPath;

            Properties props = new Properties();
            props.setProperty(DB_PATH_PROPERTY, jdbcUrl);
            environment.getPropertySources().addFirst(new PropertiesPropertySource("manifest-db-path", props));

            log.info("Overriding {} to {} (from manifest {}.output.db-path)", DB_PATH_PROPERTY, jdbcUrl, MANIFEST_FILE);
        } catch (IOException e) {
            log.warn("Failed to read {} for datasource override — using defaults: {}", MANIFEST_FILE, e.getMessage());
        }
    }
}
