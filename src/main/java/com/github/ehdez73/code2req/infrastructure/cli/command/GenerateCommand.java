package com.github.ehdez73.code2req.infrastructure.cli.command;

import com.github.ehdez73.code2req.generation.GenerateOrchestrator;
import com.github.ehdez73.code2req.generation.domain.model.SpecResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.time.Duration;
import java.time.Instant;

@ShellComponent
public class GenerateCommand {

    private static final Logger log = LoggerFactory.getLogger(GenerateCommand.class);

    private final GenerateOrchestrator generateOrchestrator;

    public GenerateCommand(GenerateOrchestrator generateOrchestrator) {
        this.generateOrchestrator = generateOrchestrator;
    }

    @ShellMethod(key = "generate", value = "Generate spec.md and semantic_manifest.json from cached extraction results")
    public String generate() {
        var sb = new StringBuilder("=== Generate ===\n\n");
        var start = Instant.now();

        try {
            SpecResult result = generateOrchestrator.generate();
            long elapsed = Duration.between(start, Instant.now()).toSeconds();

            sb.append(String.format("  Features: %d%n", result.featureCount()));
            sb.append(String.format("  Flows: %d%n", result.flowCount()));
            sb.append("  Generated files:\n");
            sb.append(String.format("    - %s%n", result.markdownPath()));
            sb.append(String.format("    - %s%n", result.manifestPath()));
            sb.append(String.format("  Generate elapsed: %ds%n%n", elapsed));

            long totalElapsed = Duration.between(start, Instant.now()).toSeconds();
            sb.append(String.format("=== Generate Complete (%ds) ===%n", totalElapsed));
        } catch (Exception e) {
            log.error("Generate failed: {}", e.getMessage(), e);
            sb.append("  Error: ").append(e.getMessage()).append("\n");
            sb.append("=== Generate Failed ===\n");
        }

        return sb.toString();
    }
}
