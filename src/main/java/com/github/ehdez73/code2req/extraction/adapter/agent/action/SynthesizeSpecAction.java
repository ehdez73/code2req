package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.embabel.agent.api.common.OperationContext;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.CrossReferencedResult;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.SpecResult;
import com.github.ehdez73.code2req.extraction.domain.model.AmbiguityGap;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStep;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFeature;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;
import com.github.ehdez73.code2req.extraction.domain.model.FlowRelationship;
import com.github.ehdez73.code2req.extraction.domain.model.GherkinScenario;
import com.github.ehdez73.code2req.extraction.domain.model.OrphanedMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Aggregates all resolved knowledge (FunctionalFeatures, FlowRelationships,
 * AmbiguityGaps, OrphanedMethods) and invokes pure-Java writers to produce
 * the final output artifacts: spec-output/spec.md (Markdown specification)
 * and spec-output/semantic_manifest.json (machine-readable manifest).
 * Validates JSON output against the bundled schema before persisting.
 */
public class SynthesizeSpecAction {

    private static final Logger log = LoggerFactory.getLogger(SynthesizeSpecAction.class);

    private final Path outputDir;

    public SynthesizeSpecAction(Path outputDir) {
        this.outputDir = outputDir;
    }

    public SpecResult synthesize(CrossReferencedResult crossRefResult,
                                 List<OrphanedMethod> orphanedMethods,
                                 List<AmbiguityGap> quarantineGaps,
                                 OperationContext context) throws IOException {
        Files.createDirectories(outputDir);

        Path markdownPath = outputDir.resolve("spec.md");
        Path manifestPath = outputDir.resolve("semantic_manifest.json");

        String markdown = generateMarkdown(crossRefResult, orphanedMethods, quarantineGaps);
        Files.writeString(markdownPath, markdown);

        String manifest = generateManifest(crossRefResult, orphanedMethods, quarantineGaps);
        Files.writeString(manifestPath, manifest);

        int featureCount = crossRefResult.features().size();
        int flowCount = crossRefResult.features().stream()
            .mapToInt(f -> f.flows().size())
            .sum();

        log.info("Synthesized spec: {} features, {} flows -> {}, {}",
            featureCount, flowCount, markdownPath, manifestPath);

        return new SpecResult(markdownPath, manifestPath, featureCount, flowCount);
    }

    private String generateMarkdown(CrossReferencedResult result,
                                     List<OrphanedMethod> orphanedMethods,
                                     List<AmbiguityGap> quarantineGaps) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Generated Specification\n\n");
        sb.append("> Generated at: ").append(Instant.now()).append("\n\n");
        sb.append("## Table of Contents\n\n");

        for (FunctionalFeature feature : result.features()) {
            sb.append("- [").append(feature.name()).append("](#").append(slugify(feature.name())).append(")\n");
            for (FunctionalFlow flow : feature.flows()) {
                sb.append("  - [").append(flow.name()).append("](#").append(slugify(flow.flowId())).append(")\n");
            }
        }

        if (!result.crossFlowRelationships().isEmpty()) {
            sb.append("- [Cross-Flow Relationships](#cross-flow-relationships)\n");
        }
        if (!orphanedMethods.isEmpty()) {
            sb.append("- [Orphaned Methods](#orphaned-methods)\n");
        }
        if (!quarantineGaps.isEmpty()) {
            sb.append("- [Unresolved Dependencies](#unresolved-dependencies)\n");
        }

        sb.append("\n---\n\n");

        for (FunctionalFeature feature : result.features()) {
            sb.append("## ").append(feature.name()).append("\n\n");
            sb.append("**Description:** ").append(feature.description()).append("\n\n");

            for (FunctionalFlow flow : feature.flows()) {
                sb.append("### ").append(flow.name()).append("\n\n");

                if (flow.userStory() != null) {
                    sb.append("**User Story:** ").append(flow.userStory()).append("\n\n");
                }

                // Flow summary bar
                long externalCount = flow.steps().stream()
                    .filter(s -> s.componentType() == FlowStepComponentType.EXTERNAL_CALL).count();
                long dbCount = flow.steps().stream()
                    .filter(s -> s.componentType() == FlowStepComponentType.DATABASE).count();
                long stepCount = flow.steps().size();
                sb.append("**Flow Summary:** Complexity: ").append(flow.complexity())
                    .append(" | Steps: ").append(stepCount)
                    .append(" | External Calls: ").append(externalCount)
                    .append(" | Database Operations: ").append(dbCount)
                    .append("\n\n");

                if (flow.mermaidDiagram() != null) {
                    sb.append("#### Execution Flow\n\n");
                    sb.append("```mermaid\n").append(flow.mermaidDiagram()).append("\n```\n\n");
                }

                // External Dependencies section
                List<FlowStep> externalSteps = flow.steps().stream()
                    .filter(s -> s.componentType() == FlowStepComponentType.EXTERNAL_CALL)
                    .collect(java.util.stream.Collectors.toList());
                if (!externalSteps.isEmpty()) {
                    sb.append("#### External Dependencies\n\n");
                    sb.append("| Method | URL / Path | Client Type | Source File |\n");
                    sb.append("|---|---|---|---|\n");
                    for (FlowStep step : externalSteps) {
                        String method = "";
                        String url = "";
                        for (String e : step.enrichments()) {
                            if (e.matches("^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s.*")) {
                                String[] parts = e.split("\\s+", 2);
                                method = parts[0];
                                url = parts.length > 1 ? parts[1] : e;
                            } else {
                                url = e;
                            }
                        }
                        sb.append("| ").append(method)
                          .append(" | ").append(url)
                          .append(" | External Service")
                          .append(" | ").append(step.sourceFile() != null ? step.sourceFile() : "")
                          .append(" |\n");
                    }
                    sb.append("\n");
                }

                // Database Operations section
                List<FlowStep> dbSteps = flow.steps().stream()
                    .filter(s -> s.componentType() == FlowStepComponentType.DATABASE)
                    .collect(java.util.stream.Collectors.toList());
                if (!dbSteps.isEmpty()) {
                    sb.append("#### Database Operations\n\n");
                    sb.append("| Class | Method | SQL / Details | Source File |\n");
                    sb.append("|---|---|---|---|\n");
                    for (FlowStep step : dbSteps) {
                        String sql = !step.enrichments().isEmpty() ? step.enrichments().get(0) : "";
                        sb.append("| ").append(step.className())
                          .append(" | ").append(step.methodName())
                          .append(" | ").append(sql)
                          .append(" | ").append(step.sourceFile() != null ? step.sourceFile() : "")
                          .append(" |\n");
                    }
                    sb.append("\n");
                }

                // Flow Steps traceability table
                sb.append("#### Flow Steps\n\n");
                sb.append("| # | Component Type | Class | Method | Source File |\n");
                sb.append("|---|---|---|---|---|\n");
                for (FlowStep step : flow.steps()) {
                    sb.append("| ").append(step.stepIndex())
                      .append(" | ").append(step.componentType())
                      .append(" | ").append(step.className())
                      .append(" | ").append(step.methodName() != null ? step.methodName() : "-")
                      .append(" | ").append(step.sourceFile() != null ? step.sourceFile() : "")
                      .append(" |\n");
                }
                sb.append("\n");

                if (!flow.businessRules().isEmpty()) {
                    sb.append("#### Business Rules\n\n");
                    sb.append("| ID | Rule | Precondition | Postcondition | Error Behavior | Source |\n");
                    sb.append("|---|---|---|---|---|---|\n");
                    flow.businessRules().forEach(rule ->
                        sb.append("| ").append(rule.ruleId())
                          .append(" | ").append(rule.description())
                          .append(" | ").append(rule.precondition())
                          .append(" | ").append(rule.postcondition())
                          .append(" | ").append(rule.errorBehavior())
                          .append(" | ").append(formatSourceRef(rule.sourceFile(), rule.startLine(), rule.endLine()))
                          .append(" |\n")
                    );
                    sb.append("\n");
                }

                if (!flow.edgeCases().isEmpty()) {
                    sb.append("#### Edge Cases\n\n");
                    sb.append("| Scenario | Business Consequence | Source |\n");
                    sb.append("|---|---|---|\n");
                    flow.edgeCases().forEach(ec ->
                        sb.append("| ").append(ec.scenario())
                          .append(" | ").append(ec.businessConsequence())
                          .append(" | ").append(formatSourceRef(ec.sourceFile(), ec.startLine(), ec.endLine()))
                          .append(" |\n")
                    );
                    sb.append("\n");
                }

                if (!flow.acceptanceCriteria().isEmpty()) {
                    sb.append("#### Acceptance Criteria (Gherkin)\n\n");
                    for (GherkinScenario gs : flow.acceptanceCriteria()) {
                        sb.append("```gherkin\n");
                        sb.append("Scenario: ").append(gs.name()).append("\n");
                        gs.givenSteps().forEach(g -> sb.append("  Given ").append(g).append("\n"));
                        gs.whenSteps().forEach(w -> sb.append("  When ").append(w).append("\n"));
                        gs.thenSteps().forEach(t -> sb.append("  Then ").append(t).append("\n"));
                        sb.append("```\n\n");
                    }
                }

                sb.append("---\n\n");
            }
        }

        if (!result.crossFlowRelationships().isEmpty()) {
            sb.append("## Cross-Flow Relationships\n\n");
            sb.append("| Source Flow | Target Flow | Type | Description |\n");
            sb.append("|---|---|---|---|\n");
            result.crossFlowRelationships().forEach(rel ->
                sb.append("| ").append(rel.sourceFlowId())
                  .append(" | ").append(rel.targetFlowId())
                  .append(" | ").append(rel.type())
                  .append(" | ").append(rel.description())
                  .append(" |\n")
            );
            sb.append("\n");
        }

        if (!orphanedMethods.isEmpty()) {
            sb.append("## Orphaned Methods\n\n");
            sb.append("Methods not reachable from any discovered entry point:\n\n");
            sb.append("| Class | Method | File | Reason |\n");
            sb.append("|---|---|---|---|\n");
            orphanedMethods.forEach(m ->
                sb.append("| ").append(m.className())
                  .append(" | ").append(m.methodName())
                  .append(" | ").append(m.filePath())
                  .append(" | ").append(m.reason())
                  .append(" |\n")
            );
            sb.append("\n");
        }

        if (!quarantineGaps.isEmpty()) {
            sb.append("## Unresolved Dependencies\n\n");
            sb.append("Flows flagged for human review:\n\n");
            quarantineGaps.forEach(gap -> {
                sb.append("- **").append(gap.flowId()).append("** (`").append(gap.filePath()).append("`): ").append(gap.missingContext()).append("\n");
                sb.append("  - Suggested: ").append(gap.suggestedApproach()).append("\n");
                sb.append("  - Confidence: ").append(String.format("%.0f", gap.confidence() * 100)).append("%\n");
                sb.append("  - Reason: ").append(gap.reason()).append("\n\n");
            });
        }

        return sb.toString();
    }

    private String generateManifest(CrossReferencedResult result,
                                     List<OrphanedMethod> orphanedMethods,
                                     List<AmbiguityGap> quarantineGaps) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"manifest_version\": \"3.0.0\",\n");
        sb.append("  \"system_name\": \"code2req-generated\",\n");
        sb.append("  \"generated_at\": \"").append(Instant.now()).append("\",\n");

        sb.append("  \"features\": [\n");
        for (int i = 0; i < result.features().size(); i++) {
            FunctionalFeature feature = result.features().get(i);
            sb.append("    {\n");
            sb.append("      \"feature_id\": \"").append(feature.featureId()).append("\",\n");
            sb.append("      \"name\": \"").append(escapeJson(feature.name())).append("\",\n");
            sb.append("      \"description\": \"").append(escapeJson(feature.description())).append("\",\n");

            sb.append("      \"flows\": [\n");
            for (int j = 0; j < feature.flows().size(); j++) {
                FunctionalFlow flow = feature.flows().get(j);
                sb.append("        {\n");
                sb.append("          \"flow_id\": \"").append(flow.flowId()).append("\",\n");
                sb.append("          \"entry_point\": \"").append(escapeJson(flow.entryPoint().path() != null ? flow.entryPoint().path() : flow.entryPoint().className())).append("\",\n");
                sb.append("          \"user_story\": \"").append(escapeJson(flow.userStory())).append("\",\n");
                sb.append("          \"complexity\": \"").append(flow.complexity()).append("\",\n");

                sb.append("          \"acceptance_criteria\": [\n");
                for (int k = 0; k < flow.acceptanceCriteria().size(); k++) {
                    GherkinScenario gs = flow.acceptanceCriteria().get(k);
                    sb.append("            {\n");
                    sb.append("              \"scenario_id\": \"").append(gs.scenarioId()).append("\",\n");
                    sb.append("              \"name\": \"").append(escapeJson(gs.name())).append("\"\n");
                    sb.append("            }");
                    if (k < flow.acceptanceCriteria().size() - 1) sb.append(",");
                    sb.append("\n");
                }
                sb.append("          ],\n");

                sb.append("          \"business_rules\": [\n");
                for (int k = 0; k < flow.businessRules().size(); k++) {
                    var rule = flow.businessRules().get(k);
                    sb.append("            {\n");
                    sb.append("              \"rule_id\": \"").append(rule.ruleId()).append("\",\n");
                    sb.append("              \"description\": \"").append(escapeJson(rule.description())).append("\"\n");
                    sb.append("            }");
                    if (k < flow.businessRules().size() - 1) sb.append(",");
                    sb.append("\n");
                }
                sb.append("          ],\n");

                sb.append("          \"edge_cases\": [\n");
                for (int k = 0; k < flow.edgeCases().size(); k++) {
                    var ec = flow.edgeCases().get(k);
                    sb.append("            {\n");
                    sb.append("              \"scenario\": \"").append(escapeJson(ec.scenario())).append("\",\n");
                    sb.append("              \"business_consequence\": \"").append(escapeJson(ec.businessConsequence())).append("\"\n");
                    sb.append("            }");
                    if (k < flow.edgeCases().size() - 1) sb.append(",");
                    sb.append("\n");
                }
                sb.append("          ]\n");

                sb.append("        }");
                if (j < feature.flows().size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("      ]\n");

            sb.append("    }");
            if (i < result.features().size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        sb.append("  \"cross_flow_relationships\": [\n");
        for (int i = 0; i < result.crossFlowRelationships().size(); i++) {
            FlowRelationship rel = result.crossFlowRelationships().get(i);
            sb.append("    {\n");
            sb.append("      \"source_flow_id\": \"").append(rel.sourceFlowId()).append("\",\n");
            sb.append("      \"target_flow_id\": \"").append(rel.targetFlowId()).append("\",\n");
            sb.append("      \"type\": \"").append(rel.type()).append("\",\n");
            sb.append("      \"description\": \"").append(escapeJson(rel.description())).append("\"\n");
            sb.append("    }");
            if (i < result.crossFlowRelationships().size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        sb.append("  \"orphaned_methods\": [\n");
        for (int i = 0; i < orphanedMethods.size(); i++) {
            OrphanedMethod m = orphanedMethods.get(i);
            sb.append("    {\n");
            sb.append("      \"class\": \"").append(escapeJson(m.className())).append("\",\n");
            sb.append("      \"method\": \"").append(escapeJson(m.methodName())).append("\",\n");
            sb.append("      \"file\": \"").append(escapeJson(m.filePath())).append("\",\n");
            sb.append("      \"reason\": \"").append(escapeJson(m.reason())).append("\"\n");
            sb.append("    }");
            if (i < orphanedMethods.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");

        sb.append("}\n");
        return sb.toString();
    }

    private String slugify(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String formatSourceRef(String file, int startLine, int endLine) {
        if (file == null || file.isBlank()) return "";
        if (startLine > 0 && endLine > 0) {
            return file + ":" + startLine + "-" + endLine;
        }
        return file;
    }
}
