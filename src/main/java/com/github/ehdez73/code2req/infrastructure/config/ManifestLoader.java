package com.github.ehdez73.code2req.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.ehdez73.code2req.common.domain.ProjectManifest;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Path;

@Service
public class ManifestLoader {
    private final ObjectMapper mapper;

    public ManifestLoader() {
        this.mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ProjectManifest load(Path path) throws IOException {
        return mapper.readValue(path.toFile(), ProjectManifest.class);
    }

    public JsonNode loadAsJsonNode(Path path) throws IOException {
        return mapper.readTree(path.toFile());
    }
}
