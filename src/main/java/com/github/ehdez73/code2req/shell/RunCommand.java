package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.config.ManifestLoader;
import com.github.ehdez73.code2req.config.ManifestValidator;
import com.github.ehdez73.code2req.model.ProjectManifest;
import com.github.ehdez73.code2req.orchestrator.CompletionStatus;
import com.github.ehdez73.code2req.orchestrator.Phase2Orchestrator;
import com.github.ehdez73.code2req.synthesis.Phase3Orchestrator;
import com.github.ehdez73.code2req.synthesis.Phase3Result;
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
public class RunCommand {

    private static final Logger log = LoggerFactory.getLogger(RunCommand.class);

    private final Phase2Orchestrator phase2Orchestrator;
    private final Phase3Orchestrator phase3Orchestrator;
    private final ManifestLoader manifestLoader;
    private final ManifestValidator manifestValidator;

    public RunCommand(Phase2Orchestrator phase2Orchestrator,
                      Phase3Orchestrator phase3Orchestrator,
                      ManifestLoader manifestLoader,
                      ManifestValidator manifestValidator) {
        this.phase2Orchestrator = phase2Orchestrator;
        this.phase3Orchestrator = phase3Orchestrator;
        this.manifestLoader = manifestLoader;
        this.manifestValidator = manifestValidator;
    }

    @ShellMethod(key = "run", value = "Executes all 3 phases end-to-end: indexing (if needed), Phase 2 semantic enrichment, Phase 3 functional extraction")
    public String run(
            @ShellOption(value = "--manifest", defaultValue = "project-manifest.yaml",
                         help = "Path to the project manifest YAML file") String manifestPath,
            @ShellOption(value = "--dry-run", defaultValue = "false",
                         help = "Simulation mode: stubs instead of LLM calls") boolean dryRun,
            @ShellOption(value = "--llm-threshold", defaultValue = ShellOption.NULL,
                         help = "Override unresolved signatures threshold (0 to skip Phase 2)") Integer llmThreshold) {

        var sb = new StringBuilder("=== Run Pipeline ===\n\n");
        var start = Instant.now();

        Path manifestFile = Path.of(manifestPath);
        if (!Files.exists(manifestFile)) {
            return "Error: Manifest file not found: " + manifestFile;
        }

        try {
            var validation = manifestValidator.validate(manifestFile);
            if (validation.hasErrors()) {
                sb.append("Manifest validation FAILED:\n");
                for (var err : validation.getErrors()) {
                    sb.append("  - ").append(err).append("\n");
                }
                return sb.toString();
            }
        } catch (IOException e) {
            return "Error: Failed to read manifest: " + e.getMessage();
        }

        boolean skipPhase2 = (llmThreshold != null && llmThreshold == 0);

        CompletionStatus phase2Status;
        if (skipPhase2) {
            sb.append("--llm-threshold is 0: skipping Phase 2 entirely\n\n");
            phase2Status = new CompletionStatus(0, 0, 0, 0, 0, 0.0, java.util.List.of());
        } else {
            sb.append("=== Phase 2: Semantic Enrichment ===\n");
            var phase2Start = Instant.now();

            if (dryRun) {
                sb.append("  Mode: DRY RUN (simulation stubs, no API calls)\n");
            }

            phase2Status = phase2Orchestrator.executePhase2(dryRun);
            long p2Elapsed = Duration.between(phase2Start, Instant.now()).toSeconds();

            sb.append(String.format("  Submitted: %d%n", phase2Status.tasksSubmitted()));
            sb.append(String.format("  Completed: %d%n", phase2Status.tasksCompleted()));
            sb.append(String.format("  Failed: %d%n", phase2Status.tasksFailed()));
            sb.append(String.format("  Dependencies discovered: %d%n", phase2Status.dependenciesDiscovered()));
            sb.append(String.format("  Tokens consumed: %d%n", phase2Status.tokensConsumed()));
            sb.append(String.format("  Estimated cost: $%.6f%n", phase2Status.apiCostEstimated()));
            if (!phase2Status.awaitingHumanReview().isEmpty()) {
                sb.append("  Awaiting human review:\n");
                for (String path : phase2Status.awaitingHumanReview()) {
                    sb.append(String.format("    - %s%n", path));
                }
            }
            sb.append(String.format("  Phase 2 elapsed: %ds%n%n", p2Elapsed));
        }

        sb.append("=== Phase 3: Functional Requirement Extraction ===\n");
        var phase3Start = Instant.now();
        Phase3Result phase3Result;

        if (dryRun) {
            sb.append("  Mode: DRY RUN (simulation, no actual synthesis)\n");
        }

        phase3Result = phase3Orchestrator.execute(phase2Status, dryRun);
        long p3Elapsed = Duration.between(phase3Start, Instant.now()).toSeconds();

        sb.append(String.format("  Flows extracted: %d%n", phase3Result.flowsExtracted()));
        sb.append(String.format("  Ambiguity gaps: %d%n", phase3Result.ambiguityGaps()));
        sb.append(String.format("  Awaiting review: %d%n", phase3Result.awaitingReview()));
        sb.append(String.format("  Phase 3 elapsed: %ds%n%n", p3Elapsed));

        long totalElapsed = Duration.between(start, Instant.now()).toSeconds();
        sb.append(String.format("=== Run Complete (%ds) ===%n", totalElapsed));

        if (dryRun) {
            sb.append("  Dry-run mode: no API calls made, no credentials required.\n");
        }

        return sb.toString();
    }
}
