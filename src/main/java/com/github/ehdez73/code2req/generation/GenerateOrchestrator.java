package com.github.ehdez73.code2req.generation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.github.ehdez73.code2req.extraction.ExtractionCache;
import com.github.ehdez73.code2req.generation.adapter.writer.SynthesizeSpecAction;
import com.github.ehdez73.code2req.generation.domain.model.SpecResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class GenerateOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GenerateOrchestrator.class);

    private static final Path DEFAULT_CACHE_PATH = Path.of("spec-output", "extraction-cache.json");

    private final ObjectMapper objectMapper;
    private final Path cachePath;

    public GenerateOrchestrator() {
        this(DEFAULT_CACHE_PATH);
    }

    GenerateOrchestrator(Path cachePath) {
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.cachePath = cachePath;
    }

    public SpecResult generate() throws IOException {
        log.info("Generate: reading extraction cache from {}", cachePath);
        if (!Files.exists(cachePath)) {
            throw new IllegalStateException(
                "No extraction cache found at " + cachePath + ". Run 'extract' first.");
        }

        ExtractionCache cache;
        try {
            cache = objectMapper.readValue(cachePath.toFile(), ExtractionCache.class);
        } catch (InvalidTypeIdException e) {
            throw new IllegalStateException(
                "Extraction cache is in an incompatible format. Run 'extract' again to regenerate it.", e);
        }
        SynthesizeSpecAction action = new SynthesizeSpecAction(cachePath.getParent());
        return action.synthesize(
            cache.crossRefResult(), cache.orphanedMethods(), cache.quarantineGaps(), null);
    }
}
