package com.github.ehdez73.code2req.infrastructure.cli.command;

import com.github.ehdez73.code2req.infrastructure.config.ManifestValidator;
import com.github.ehdez73.code2req.extraction.ExtractionOrchestrator;
import com.github.ehdez73.code2req.extraction.ExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

@ShellComponent
public class ExtractCommand {

    private static final Logger log = LoggerFactory.getLogger(ExtractCommand.class);

    private final ExtractionOrchestrator extractionOrchestrator;
    private final ManifestValidator manifestValidator;

    public ExtractCommand(ExtractionOrchestrator extractionOrchestrator,
                          ManifestValidator manifestValidator) {
        this.extractionOrchestrator = extractionOrchestrator;
        this.manifestValidator = manifestValidator;
    }

    @ShellMethod(key = "extract", value = "Run Phase 3 functional requirement extraction from enriched tasks")
    public String extract(
            @ShellOption(value = "--manifest", defaultValue = "project-manifest.yaml",
                         help = "Path to the project manifest YAML file") String manifestPath,
            @ShellOption(value = "--dry-run", defaultValue = "false",
                         help = "Simulation mode: no actual extraction") boolean dryRun,
            @ShellOption(value = "--force", defaultValue = "false",
                         help = "Force re-execution even if no new enrichments") boolean force) {

        var sb = new StringBuilder("=== Extract ===\n\n");
        var start = Instant.now();

        Path manifestFile = Path.of(manifestPath);
        if (!validateManifest(manifestFile, sb)) {
            return sb.toString();
        }

        sb.append("=== Phase 3: Functional Requirement Extraction ===\n");
        var phase3Start = Instant.now();

        if (dryRun) {
            sb.append("  Mode: DRY RUN (simulation, no actual synthesis)\n");
        }
        if (force) {
            sb.append("  Mode: FORCE (re-executing Phase 3)\n");
        }

        ExtractionResult result = extractionOrchestrator.execute(dryRun, force);
        long p3Elapsed = Duration.between(phase3Start, Instant.now()).toSeconds();

        if (result.isBlocked()) {
            sb.append("  Phase 3 BLOCKED: ").append(result.blockedReason()).append("\n");
            sb.append(String.format("  Phase 3 elapsed: %ds%n%n", p3Elapsed));
            long totalElapsed = Duration.between(start, Instant.now()).toSeconds();
            sb.append(String.format("=== Extract Complete (%ds) ===%n", totalElapsed));
            return sb.toString();
        }

        sb.append(String.format("  Flows extracted: %d%n", result.flowsExtracted()));
        if (!result.flowNames().isEmpty()) {
            sb.append("  Flows: ");
            sb.append(String.join(", ", result.flowNames()));
            sb.append("\n");
        }
        sb.append(String.format("  Ambiguity gaps: %d%n", result.ambiguityGaps()));
        sb.append(String.format("  Awaiting review: %d%n", result.awaitingReview()));
        if (!result.generatedFiles().isEmpty()) {
            sb.append("  Generated files:\n");
            for (var f : result.generatedFiles()) {
                sb.append(String.format("    - %s%n", f));
            }
        }
        sb.append(String.format("  Phase 3 elapsed: %ds%n%n", p3Elapsed));

        long totalElapsed = Duration.between(start, Instant.now()).toSeconds();
        sb.append(String.format("=== Extract Complete (%ds) ===%n", totalElapsed));
        if (dryRun) {
            sb.append("  Dry-run mode: no API calls made, no credentials required.\n");
        }
        if (force) {
            sb.append("  Force mode: Phase 3 re-executed.\n");
        }

        return sb.toString();
    }

    private boolean validateManifest(Path manifestFile, StringBuilder sb) {
        if (!Files.exists(manifestFile)) {
            sb.append("Error: Manifest file not found: ").append(manifestFile);
            return false;
        }
        try {
            var validation = manifestValidator.validate(manifestFile);
            if (validation.hasErrors()) {
                sb.append("Manifest validation FAILED:\n");
                for (var err : validation.getErrors()) {
                    sb.append("  - ").append(err).append("\n");
                }
                return false;
            }
            return true;
        } catch (IOException e) {
            sb.append("Error: Failed to read manifest: ").append(e.getMessage());
            return false;
        }
    }
}
