package com.github.ehdez73.code2req.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ExecutionFinding(
    Metadata metadata,
    @JsonProperty("business_abstraction") BusinessAbstraction businessAbstraction,
    @JsonProperty("business_rules_and_guardrails") BusinessRulesAndGuardrails businessRulesAndGuardrails,
    @JsonProperty("test_insights") List<TestInsight> testInsights,
    @JsonProperty("architectural_connections") ArchitecturalConnections architecturalConnections,
    @JsonProperty("discovered_dependencies") List<DiscoveredDependency> discoveredDependencies
) {
    public record Metadata(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("target_name") String targetName,
        @JsonProperty("file_path") String filePath,
        @JsonProperty("tech_profile") String techProfile,
        @JsonProperty("module_tag") String moduleTag,
        String timestamp
    ) {}

    public record BusinessAbstraction(
        String purpose,
        @JsonProperty("happy_paths") List<HappyPath> happyPaths
    ) {}

    public record HappyPath(
        @JsonProperty("flow_name") String flowName,
        String description
    ) {}

    public record BusinessRulesAndGuardrails(
        List<Validation> validations,
        @JsonProperty("edge_cases") List<EdgeCase> edgeCases
    ) {}

    public record Validation(
        @JsonProperty("field_or_context") String fieldOrContext,
        String rule,
        @JsonProperty("error_behavior") String errorBehavior
    ) {}

    public record EdgeCase(
        String scenario,
        @JsonProperty("business_consequence") String businessConsequence
    ) {}

    public record TestInsight(
        @JsonProperty("test_file_path") String testFilePath,
        @JsonProperty("scenario_verified") String scenarioVerified,
        @JsonProperty("hidden_rule_uncovered") String hiddenRuleUncovered
    ) {}

    public record ArchitecturalConnections(
        Inbound inbound,
        Outbound outbound
    ) {}

    public record Inbound(
        @JsonProperty("http_endpoints") List<HttpEndpoint> httpEndpoints,
        @JsonProperty("event_subscriptions") List<EventSubscription> eventSubscriptions,
        @JsonProperty("scheduled_triggers") List<ScheduledTrigger> scheduledTriggers
    ) {}

    public record HttpEndpoint(
        String method,
        @JsonProperty("path_pattern") String pathPattern,
        String description
    ) {}

    public record EventSubscription(
        String broker,
        @JsonProperty("topic_or_queue") String topicOrQueue,
        @JsonProperty("payload_structure") String payloadStructure
    ) {}

    public record ScheduledTrigger(
        @JsonProperty("schedule_expression") String scheduleExpression,
        String description
    ) {}

    public record Outbound(
        @JsonProperty("http_calls") List<HttpCall> httpCalls,
        @JsonProperty("event_publications") List<EventPublication> eventPublications
    ) {}

    public record HttpCall(
        String method,
        @JsonProperty("url_or_path") String urlOrPath,
        @JsonProperty("encapsulated_in") String encapsulatedIn,
        @JsonProperty("is_external") boolean isExternal,
        @JsonProperty("external_contract_hint") String externalContractHint
    ) {}

    public record EventPublication(
        String broker,
        @JsonProperty("topic_or_queue") String topicOrQueue,
        @JsonProperty("routing_key") String routingKey,
        @JsonProperty("business_trigger") String businessTrigger
    ) {}

    public record DiscoveredDependency(
        @JsonProperty("file_path") String filePath,
        String reason,
        @JsonProperty("discovery_depth") int discoveryDepth
    ) {}
}
