package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.embabel.agent.api.common.OperationContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ehdez73.code2req.common.util.HashUtils;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.AnalyzedFlowResult;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.GroupedFlowsResult;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFeature;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;
import com.github.ehdez73.code2req.extraction.domain.model.HttpEntryPoint;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Clusters related FunctionalFlows into FunctionalFeatures using semantic
 * similarity from Phase 2 enrichment (keyword overlap). Assigns feature
 * names and descriptions. Falls back to package/directory proximity when
 * enrichment data is unavailable.
 */
public class GroupFlowsAction {

    private static final Logger log = LoggerFactory.getLogger(GroupFlowsAction.class);

    private final ExecutionFindingStore executionFindingStore;
    private final ObjectMapper objectMapper;
    private final boolean resume;

    public GroupFlowsAction(ExecutionFindingStore executionFindingStore, ObjectMapper objectMapper, boolean resume) {
        this.executionFindingStore = executionFindingStore;
        this.objectMapper = objectMapper;
        this.resume = resume;
    }

    public GroupedFlowsResult group(AnalyzedFlowResult analyzedResult, OperationContext context) {


        List<FunctionalFlow> flows = analyzedResult.flows();

        if (flows.isEmpty()) {
            return new GroupedFlowsResult(List.of());
        }

        String groupingKey = deterministicGroupKey(flows);

        if (resume) {
            List<Map<String, Object>> existing = executionFindingStore.findByTaskIdAndType(groupingKey, FindingType.FLOW_GROUPING);
            if (!existing.isEmpty()) {
                try {
                    String json = (String) existing.get(0).get("finding_json");
                    GroupingResponse cached = objectMapper.readValue(json, GroupingResponse.class);
                    log.info("Reusing cached flow grouping for {} flows", flows.size());
                    List<FunctionalFeature> features = buildFeatures(cached, flows);
                    log.info("Grouped {} flows into {} features", analyzedResult.flows().size(), features.size());
                    return new GroupedFlowsResult(features);
                } catch (Exception e) {
                    log.warn("Failed to deserialize cached FLOW_GROUPING: {}", e.getMessage());
                }
            }
        }

        if (flows.size() == 1) {
            FunctionalFlow flow = flows.get(0);
            String description = generateFeatureDescription(flow, context);
            FunctionalFeature feature = new FunctionalFeature(
                "feature-1",
                deriveFeatureName(flow),
                description,
                flows,
                List.of()
            );
            return new GroupedFlowsResult(List.of(feature));
        }

        String flowSummaries = flows.stream()
            .map(f -> "Flow " + shortId(f.flowId()) + ": " + f.name() + " - " + truncate(f.userStory(), 200)
                + " | Entry: " + f.entryPoint().type()
                + " | Steps: " + f.steps().size()
                + " | External: " + f.steps().stream().filter(s -> s.componentType() == FlowStepComponentType.EXTERNAL_CALL).count()
                + " | DB: " + f.steps().stream().filter(s -> s.componentType() == FlowStepComponentType.DATABASE).count())
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");

        String prompt = """
            You are analyzing a set of execution flows extracted from a codebase and organizing them into logical, business-oriented feature groups.
        
            ## Task
            Group the flows below into cohesive features based on semantic similarity. A "feature" represents a business capability a stakeholder would recognize (e.g., "User Registration", "Order Fulfillment") — not a technical layer or file structure.
        
            ## Grouping criteria
            Flows likely belong together when they meet one or more of the following:
            - Operate on the same domain entity (e.g., Orders, Users)
            - Share the same business purpose or user-facing outcome
            - Are part of the same CRUD lifecycle for an entity
            - Share the same external dependencies, databases, or third-party integrations
        
            These are signals, not strict requirements — use judgment. A flow does not need to satisfy every criterion to belong in a group.
        
            ## Rules
            - Every flow ID provided below must appear in exactly one group. Do not omit any flow ID and do not duplicate a flow ID across groups.
            - If a flow doesn't clearly fit with others, place it in its own single-flow group rather than forcing it into an unrelated one.
            - Do not invent flow IDs that are not present in the input.
            - featureName should be a short, human-readable noun phrase (2-4 words, Title Case), unique across groups.
            - featureDescription must be 2-3 sentences covering: (1) the business capability, (2) what triggers it, and (3) any external dependencies or systems involved.
            - Prefer fewer, more meaningful groups over many overly granular ones, but never merge flows that don't genuinely share a business capability.
        
            ## Output format
            Return ONLY a single valid JSON object — no markdown code fences, no commentary, no explanation before or after.
        
            {
              "groups": [
                {
                  "featureName": "...",
                  "featureDescription": "...",
                  "flowIds": ["flow-id-1", "flow-id-2"]
                }
              ]
            }
        
            ## Flows
            %s
            """.formatted(flowSummaries);


        GroupingResponse response = context.ai()
            .withDefaultLlm()
            .createObject(prompt, GroupingResponse.class);

        try {
            String json = objectMapper.writeValueAsString(response);
            executionFindingStore.save(groupingKey, FindingType.FLOW_GROUPING, json, true);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize FLOW_GROUPING: {}", e.getMessage());
        }

        List<FunctionalFeature> features = buildFeatures(response, flows);

        log.info("Grouped {} flows into {} features", analyzedResult.flows().size(), features.size());
        return new GroupedFlowsResult(features);
    }

    private String generateFeatureDescription(FunctionalFlow flow, OperationContext context) {

        String descKey = "GRP-DESC:" + flow.flowId();

        if (resume) {
            List<Map<String, Object>> existing = executionFindingStore.findByTaskIdAndType(descKey, FindingType.FLOW_GROUPING);
            if (!existing.isEmpty()) {
                try {
                    String json = (String) existing.get(0).get("finding_json");
                    DescriptionResponse cached = objectMapper.readValue(json, DescriptionResponse.class);
                    if (cached != null && cached.description() != null && !cached.description().isBlank()) {
                        log.info("Reusing cached feature description for {}", flow.flowId());
                        return cached.description();
                    }
                } catch (Exception e) {
                    log.warn("Failed to deserialize cached FLOW_GROUPING description for {}: {}", flow.flowId(), e.getMessage());
                }
            }
        }

        String stepsSummary = flow.steps().stream()
            .map(s -> "  - " + s.componentType() + ": " + s.className()
                + (s.methodName() != null ? "." + s.methodName() : "")
                + (!s.enrichments().isEmpty() ? " (" + String.join(", ", s.enrichments()) + ")" : ""))
            .collect(Collectors.joining("\n"));

        long externalCount = flow.steps().stream()
            .filter(s -> s.componentType() == FlowStepComponentType.EXTERNAL_CALL).count();
        long dbCount = flow.steps().stream()
            .filter(s -> s.componentType() == FlowStepComponentType.DATABASE).count();

        String prompt = """
            You are writing a short, business-oriented description of a software feature for a non-technical stakeholder audience (e.g., a product catalog or documentation index).
        
            ## Task
            Write a concise feature description in 2-3 sentences that covers:
            1. What the feature does, in terms of business/user outcome (not implementation detail)
            2. What triggers it (e.g., an HTTP request, a scheduled job, an event)
            3. Any external dependencies it relies on (external APIs, databases, event brokers) — only mention this if the data below indicates at least one exists
        
            ## Guidelines
            - Write in plain business language. Avoid class names, method names, or code-level jargon unless no other way exists to describe the trigger.
            - Base the description only on the information provided below. Do not invent business context, users, or dependencies that aren't implied by the data.
            - If "External calls" is 0, do not mention external APIs. If "Database ops" is 0, do not mention databases.
            - If a user story is provided, use it to inform the business framing and intent. If it is "N/A", infer the likely business purpose from the flow name and steps instead, but keep the inference conservative and grounded in what's given.
            - Do not return a title, label, or preamble like "Description:" — return only the 2-3 sentence description as plain prose, no JSON, no markdown.
        
            ## Flow details
            Flow name: %s
            Entry point: %s %s
            Steps:
            %s
            External calls: %d
            Database ops: %d
            User story: %s
            """.formatted(
                flow.name(),
                switch (flow.entryPoint()) {
                    case HttpEntryPoint h -> h.httpMethod();
                    default -> flow.entryPoint().type().name();
                },
                switch (flow.entryPoint()) {
                    case HttpEntryPoint h -> h.path();
                    default -> flow.entryPoint().className();
                },
                stepsSummary,
                externalCount,
                dbCount,
                flow.userStory() != null ? flow.userStory() : "N/A"
        );



        DescriptionResponse response = context.ai()
            .withDefaultLlm()
            .createObject(prompt, DescriptionResponse.class);

        try {
            String json = objectMapper.writeValueAsString(response);
            executionFindingStore.save(descKey, FindingType.FLOW_GROUPING, json, true);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize FLOW_GROUPING description for {}: {}", flow.flowId(), e.getMessage());
        }

        if (response != null && response.description() != null && !response.description().isBlank()) {
            return response.description();
        }

        return flow.userStory() != null ? flow.userStory() : "Feature for " + flow.name();
    }

    private String deriveFeatureName(FunctionalFlow flow) {
        if (flow.entryPoint() instanceof HttpEntryPoint h) {
            String path = h.path();
            String[] segments = path.split("/");
            for (int i = segments.length - 1; i >= 0; i--) {
                if (!segments[i].isEmpty() && !segments[i].startsWith("{")) {
                    return capitalize(segments[i]);
                }
            }
        }
        return flow.entryPoint().className();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private List<FunctionalFeature> buildFeatures(GroupingResponse response, List<FunctionalFlow> allFlows) {
        List<FunctionalFlow> remaining = new ArrayList<>(allFlows);
        List<FunctionalFeature> features = new ArrayList<>();

        if (response.groups() != null) {
            int featureIndex = 1;
            for (GroupDto group : response.groups()) {
                List<FunctionalFlow> matchedFlows = new ArrayList<>();
                if (group.flowIds() != null) {
                    for (String flowId : group.flowIds()) {
                        remaining.stream()
                            .filter(f -> shortId(f.flowId()).equals(flowId))
                            .findFirst()
                            .ifPresent(matchedFlows::add);
                    }
                }

                if (matchedFlows.isEmpty()) {
                    matchedFlows.addAll(remaining);
                    remaining.clear();
                } else {
                    remaining.removeAll(matchedFlows);
                }

                features.add(new FunctionalFeature(
                    "feature-" + featureIndex++,
                    group.featureName(),
                    group.featureDescription(),
                    matchedFlows,
                    List.of()
                ));
            }
        }

        if (!remaining.isEmpty()) {
            int featureIndex = features.size() + 1;
            features.add(new FunctionalFeature(
                "feature-" + featureIndex,
                "Ungrouped Flows",
                "Flows that could not be automatically grouped",
                remaining,
                List.of()
            ));
        }

        return features;
    }

    private static String shortId(String id) {
        return HashUtils.sha256Hex(id).substring(0, 8);
    }

    private static String deterministicGroupKey(List<FunctionalFlow> flows) {
        return "GRP:" + flows.stream()
            .map(FunctionalFlow::flowId)
            .sorted()
            .collect(Collectors.joining("|"));
    }

    record DescriptionResponse(String description) {}

    record GroupingResponse(List<GroupDto> groups) {}

    record GroupDto(
        String featureName,
        String featureDescription,
        List<String> flowIds
    ) {}
}
