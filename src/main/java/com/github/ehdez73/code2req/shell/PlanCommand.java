package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.config.ManifestLoader;
import com.github.ehdez73.code2req.config.ManifestValidator;
import com.github.ehdez73.code2req.model.PlannerDecision;
import com.github.ehdez73.code2req.model.ProjectManifest;
import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.planner.Phase2Planner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ShellComponent
public class PlanCommand {

    private static final Logger log = LoggerFactory.getLogger(PlanCommand.class);

    private final Phase2Planner planner;
    private final ManifestLoader manifestLoader;
    private final ManifestValidator manifestValidator;

    public PlanCommand(Phase2Planner planner,
                       ManifestLoader manifestLoader,
                       ManifestValidator manifestValidator) {
        this.planner = planner;
        this.manifestLoader = manifestLoader;
        this.manifestValidator = manifestValidator;
    }

    @ShellMethod(key = "plan", value = "Evaluate INDEXED tasks, transition qualified ones to ENRICH_PENDING, and show the enrichment plan")
    public String plan(
            @ShellOption(value = "--manifest", defaultValue = "project-manifest.yaml",
                         help = "Path to the project manifest YAML file") String manifestPath) {

        var sb = new StringBuilder("=== Plan — Dry DAG View ===\n\n");

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

        List<PlannerDecision> decisions = planner.plan();

        if (decisions.isEmpty()) {
            sb.append("No tasks found — run 'scan' first to populate the task store.\n");
            return sb.toString();
        }

        var qualified = decisions.stream()
            .filter(PlannerDecision::qualified)
            .toList();
        var nonQualified = decisions.stream()
            .filter(d -> !d.qualified())
            .toList();

        sb.append(String.format("Total tasks evaluated: %d%n", decisions.size()));
        sb.append(String.format("  Qualified: %d%n", qualified.size()));
        sb.append(String.format("  Not qualified: %d%n", nonQualified.size()));
        sb.append("\n");

        if (!qualified.isEmpty()) {
            sb.append("=== Qualified Tasks (enrichment candidates) ===\n\n");
            for (PlannerDecision d : qualified) {
                String reasons = d.reasons().stream()
                    .filter(r -> r != QualificationReason.NONE)
                    .map(QualificationReason::name)
                    .collect(Collectors.joining(", "));
                String tid = d.taskId().length() > 8 ? d.taskId().substring(0, 8) : d.taskId();
                sb.append(String.format("  [%s] %s%n", tid, d.filePath()));
                sb.append(String.format("         Reasons: %s%n", reasons));
            }
            sb.append("\n");
        }

        if (!nonQualified.isEmpty()) {
            sb.append("=== Non-Qualified Tasks ===\n\n");
            for (PlannerDecision d : nonQualified) {
                String tid = d.taskId().length() > 8 ? d.taskId().substring(0, 8) : d.taskId();
                sb.append(String.format("  [%s] %s%n", tid, d.filePath()));
            }
            sb.append("\n");
        }

        if (qualified.isEmpty()) {
            sb.append("No tasks qualified for LLM enrichment. ");
            sb.append("Consider lowering 'llm-unresolved-threshold' in your manifest ");
            sb.append("or running with --llm-threshold on the 'run' command.\n");
        }

        sb.append("Zero LLM calls made — qualified tasks transitioned to ENRICH_PENDING. Run `run` to enrich.\n");
        return sb.toString();
    }
}
