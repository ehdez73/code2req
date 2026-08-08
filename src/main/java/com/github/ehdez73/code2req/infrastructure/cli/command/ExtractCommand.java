package com.github.ehdez73.code2req.infrastructure.cli.command;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ehdez73.code2req.extraction.ExtractionCache;
import com.github.ehdez73.code2req.extraction.ExtractionOrchestrator;
import com.github.ehdez73.code2req.extraction.ExtractionResult;
import com.github.ehdez73.code2req.infrastructure.config.ManifestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final Path cacheFilePath;
    private final ObjectMapper objectMapper;

    public ExtractCommand(ExtractionOrchestrator extractionOrchestrator,
                          ManifestValidator manifestValidator,
                          @Value("${code2req.output.spec-dir}") String specDir,
                          @Value("${code2req.output.extraction-cache-file}") String cacheFile) {
        this.extractionOrchestrator = extractionOrchestrator;
        this.manifestValidator = manifestValidator;
        this.cacheFilePath = Path.of(specDir, cacheFile).normalize();
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @ShellMethod(key = "extract", value = "Run Phase 3 agentic analysis and cache results for spec generation")
    public String extract(
            @ShellOption(value = "--manifest", defaultValue = "project-manifest.yaml",
                         help = "Path to the project manifest YAML file") String manifestPath,
            @ShellOption(value = "--dry-run", defaultValue = "false",
                         help = "Simulation mode: no actual extraction") boolean dryRun,
            @ShellOption(value = "--force", defaultValue = "false",
                         help = "Force re-execution even if no new enrichments") boolean force,
            @ShellOption(value = "--resume", defaultValue = "false",
                         help = "Resume previous extraction: reuse cached per-flow LLM results") boolean resume,
            @ShellOption(value = "--flow", defaultValue = ShellOption.NULL,
                         help = "Process a single flow by short ID") String flowId,
            @ShellOption(value = "--regroup", defaultValue = "false",
                         help = "Force re-grouping and cross-referencing of all flows after single-flow extraction") boolean regroup) {

        var sb = new StringBuilder("=== Extract ===\n\n");
        var start = Instant.now();

        Path manifestFile = Path.of(manifestPath);
        if (!validateManifest(manifestFile, sb)) {
            return sb.toString();
        }

        if (flowId == null) {
            if (!force && cacheExists() && !dryRun && !resume) {
                int cachedFlows = countCachedFlows();
                return "Cache exists with " + cachedFlows + " flows from previous sessions.\n"
                    + "This will REPLACE the entire cache.\n"
                    + "Use --force to proceed, or extract --flow <id> to process individual flows.";
            }

            sb.append("=== Phase 3: Functional Requirement Extraction ===\n");
            var phase3Start = Instant.now();

            if (dryRun) sb.append("  Mode: DRY RUN\n");
            if (force) sb.append("  Mode: FORCE\n");
            if (resume) sb.append("  Mode: RESUME\n");

            ExtractionResult result = extractionOrchestrator.execute(dryRun, force, resume);
            appendResult(sb, result, start, phase3Start, dryRun, force, resume);
        } else {
            sb.append("=== Single-Flow Extraction ===\n");
            sb.append("  Flow: ").append(flowId).append("\n");
            if (regroup) sb.append("  Mode: REGROUP\n");
            if (dryRun) sb.append("  Mode: DRY RUN\n");
            if (resume) sb.append("  Mode: RESUME\n");

            try {
                ExtractionResult result = extractionOrchestrator.executeFlow(
                    flowId, regroup, dryRun, resume);
                appendResult(sb, result, start, Instant.now(), dryRun, false, resume);
            } catch (IllegalStateException | IllegalArgumentException e) {
                return e.getMessage();
            }
        }

        return sb.toString();
    }

    private void appendResult(StringBuilder sb, ExtractionResult result,
                               Instant start, Instant phaseStart,
                               boolean dryRun, boolean force, boolean resume) {
        long elapsed = Duration.between(phaseStart, Instant.now()).toSeconds();

        if (result.isBlocked()) {
            sb.append("  Phase 3 BLOCKED: ").append(result.blockedReason()).append("\n");
            sb.append(String.format("  Elapsed: %ds%n%n", elapsed));
            long totalElapsed = Duration.between(start, Instant.now()).toSeconds();
            sb.append(String.format("=== Extract Complete (%ds) ===%n", totalElapsed));
            throw new IllegalStateException(sb.toString());
        }

        if (result.flowsExtracted() > 0) {
            if (result.flowNames() != null && !result.flowNames().isEmpty()) {
                sb.append(String.format("  Flow: %s%n", String.join(", ", result.flowNames())));
            }
        }
        sb.append(String.format("  Flows extracted: %d%n", result.flowsExtracted()));
        sb.append(String.format("  Ambiguity gaps: %d%n", result.ambiguityGaps()));
        if (!result.generatedFiles().isEmpty()) {
            sb.append("  Cache file:\n");
            for (var f : result.generatedFiles()) {
                sb.append(String.format("    - %s%n", f));
            }
        }
        sb.append(String.format("  Elapsed: %ds%n%n", elapsed));

        long totalElapsed = Duration.between(start, Instant.now()).toSeconds();
        sb.append(String.format("=== Extract Complete (%ds) ===%n", totalElapsed));
        if (dryRun) sb.append("  Dry-run mode: no API calls made.\n");
        if (force) sb.append("  Force mode: cache replaced.\n");
        if (resume) sb.append("  Resume mode: reused cached analyses.\n");
    }

    private boolean cacheExists() {
        return Files.exists(cacheFilePath);
    }

    private int countCachedFlows() {
        try {
            ExtractionCache cache = objectMapper.readValue(cacheFilePath.toFile(), ExtractionCache.class);
            if (cache != null && cache.crossRefResult() != null && cache.crossRefResult().features() != null) {
                return cache.crossRefResult().features().stream()
                    .mapToInt(f -> f.flows() != null ? f.flows().size() : 0)
                    .sum();
            }
        } catch (Exception e) {
            log.debug("Could not count cached flows: {}", e.getMessage());
        }
        return 0;
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
