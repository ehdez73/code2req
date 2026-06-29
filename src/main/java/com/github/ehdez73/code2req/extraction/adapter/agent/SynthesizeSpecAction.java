package com.github.ehdez73.code2req.extraction.adapter.agent;

import com.embabel.agent.api.common.OperationContext;
import com.github.ehdez73.code2req.extraction.domain.model.AmbiguityGap;
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

                if (flow.mermaidDiagram() != null) {
                    sb.append("#### Execution Flow\n\n");
                    sb.append("```mermaid\n").append(flow.mermaidDiagram()).append("\n```\n\n");
                }

                if (!flow.businessRules().isEmpty()) {
                    sb.append("#### Business Rules\n\n");
                    sb.append("| ID | Rule | Precondition | Postcondition | Error Behavior |\n");
                    sb.append("|---|---|---|---|---|\n");
                    flow.businessRules().forEach(rule ->
                        sb.append("| ").append(rule.ruleId())
                          .append(" | ").append(rule.description())
                          .append(" | ").append(rule.precondition())
                          .append(" | ").append(rule.postcondition())
                          .append(" | ").append(rule.errorBehavior())
                          .append(" |\n")
                    );
                    sb.append("\n");
                }

                if (!flow.edgeCases().isEmpty()) {
                    sb.append("#### Edge Cases\n\n");
                    sb.append("| Scenario | Business Consequence |\n");
                    sb.append("|---|---|\n");
                    flow.edgeCases().forEach(ec ->
                        sb.append("| ").append(ec.scenario())
                          .append(" | ").append(ec.businessConsequence())
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
                sb.append("- **").append(gap.flowId()).append("**: ").append(gap.missingContext()).append("\n");
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
}
