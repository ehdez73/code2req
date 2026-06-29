package com.github.ehdez73.code2req.enrichment.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record ExecutionFinding(
    @JsonProperty(required = true) @JsonPropertyDescription("Identifies the task and source file being enriched. All fields are known values — copy them from the task context provided in the prompt.")
    Metadata metadata,
    @JsonProperty(value = "business_abstraction", required = true) @JsonPropertyDescription("High-level business purpose and happy-path flows of the source file's primary class or logic.")
    BusinessAbstraction businessAbstraction,
    @JsonProperty(value = "business_rules_and_guardrails", required = true) @JsonPropertyDescription("Explicit validation rules, implicit business guardrails, and edge cases extracted from the code or its tests.")
    BusinessRulesAndGuardrails businessRulesAndGuardrails,
    @JsonProperty(value = "test_insights", required = true) @JsonPropertyDescription("Insights mined from the associated test file: what scenarios are verified and what hidden rules the tests reveal.")
    List<TestInsight> testInsights,
    @JsonProperty(value = "architectural_connections", required = true) @JsonPropertyDescription("How this code is reached (inbound) and what it calls (outbound). Only populate if the source code contains explicit HTTP endpoints, event listeners, or outbound calls. Provide empty arrays/objects if none found.")
    ArchitecturalConnections architecturalConnections,
    @JsonProperty(value = "discovered_dependencies", required = true) @JsonPropertyDescription("References to other source files discovered during enrichment that are not already indexed in the call graph (e.g. reflection-based wiring, SPI lookups, factory class routing).")
    List<DiscoveredDependency> discoveredDependencies
) {
    public record Metadata(
        @JsonProperty(value = "task_id", required = true) String taskId,
        @JsonProperty(value = "target_name", required = true) String targetName,
        @JsonProperty(value = "file_path", required = true) String filePath,
        @JsonProperty(value = "tech_profile", required = true) String techProfile,
        @JsonProperty(value = "module_tag", required = true) String moduleTag,
        @JsonProperty(required = true) String timestamp
    ) {}

    public record BusinessAbstraction(
        @JsonPropertyDescription("1-2 sentence summary of this file's business responsibility. What business goal does it accomplish?")
        String purpose,
        @JsonProperty("happy_paths") @JsonPropertyDescription("The expected normal execution flows (success scenarios) this code supports.")
        List<HappyPath> happyPaths
    ) {}

    public record HappyPath(
        @JsonProperty("flow_name") @JsonPropertyDescription("Short name for this happy-path flow (e.g. 'Standard flow', 'Bulk upload flow').")
        String flowName,
        @JsonPropertyDescription("Detailed description of the flow: trigger, steps, and expected outcome.")
        String description
    ) {}

    public record BusinessRulesAndGuardrails(
        @JsonPropertyDescription("Business rejection rules and input validation constraints enforced by this code.")
        List<Validation> validations,
        @JsonProperty("edge_cases") @JsonPropertyDescription("Boundary conditions, error scenarios, and exceptional states the code handles.")
        List<EdgeCase> edgeCases
    ) {}

    public record Validation(
        @JsonProperty("field_or_context") @JsonPropertyDescription("The input field, parameter, or context being validated (e.g. 'email', 'order.total', 'request body').")
        String fieldOrContext,
        @JsonPropertyDescription("The validation rule or business constraint (e.g. 'Email must match pattern ^.+@.+\\..+$', 'Total must be positive').")
        String rule,
        @JsonProperty("error_behavior") @JsonPropertyDescription("What happens when validation fails (e.g. 'Throws IllegalArgumentException', 'Returns 400 Bad Request', 'Rejects with error message').")
        String errorBehavior
    ) {}

    public record EdgeCase(
        @JsonPropertyDescription("Description of the edge case scenario (e.g. 'Null input', 'Empty list', 'Expired token').")
        String scenario,
        @JsonProperty("business_consequence") @JsonPropertyDescription("Business impact if this edge case is triggered (e.g. 'Order rejected', 'Fallback to default value').")
        String businessConsequence
    ) {}

    public record TestInsight(
        @JsonProperty("test_file_path") @JsonPropertyDescription("Path to the test file that was analyzed alongside the production code.")
        String testFilePath,
        @JsonProperty("scenario_verified") @JsonPropertyDescription("The test scenario or use case being verified (e.g. 'Payment succeeds with valid card').")
        String scenarioVerified,
        @JsonProperty("hidden_rule_uncovered") @JsonPropertyDescription("A validation rule or business invariant that the test asserts but is not obvious from production code alone.")
        String hiddenRuleUncovered
    ) {}

    public record ArchitecturalConnections(
        @JsonPropertyDescription("How external callers reach this code: HTTP endpoints exposed, events consumed, or scheduled triggers.")
        Inbound inbound,
        @JsonPropertyDescription("Outbound calls this code makes: HTTP calls to external services or events it publishes.")
        Outbound outbound
    ) {}

    public record Inbound(
        @JsonProperty("http_endpoints") @JsonPropertyDescription("REST/HTTP endpoints this class exposes (from @RequestMapping, @GetMapping, @PostMapping, etc.).")
        List<HttpEndpoint> httpEndpoints,
        @JsonProperty("event_subscriptions") @JsonPropertyDescription("Event/topic subscriptions this class listens to (from @KafkaListener, @RabbitListener, @JmsListener, etc.).")
        List<EventSubscription> eventSubscriptions,
        @JsonProperty("scheduled_triggers") @JsonPropertyDescription("Scheduled tasks that trigger this code (from @Scheduled, cron expressions, etc.).")
        List<ScheduledTrigger> scheduledTriggers
    ) {}

    public record HttpEndpoint(
        @JsonPropertyDescription("HTTP method (GET, POST, PUT, DELETE, PATCH, etc.).")
        String method,
        @JsonProperty("path_pattern") @JsonPropertyDescription("URL path pattern (e.g. '/api/v1/orders/{id}').")
        String pathPattern,
        @JsonPropertyDescription("Business description of what this endpoint does.")
        String description
    ) {}

    public record EventSubscription(
        @JsonPropertyDescription("Message broker type (e.g. 'kafka', 'rabbitmq', 'jms', 'activemq').")
        String broker,
        @JsonProperty("topic_or_queue") @JsonPropertyDescription("Topic or queue name subscribed to.")
        String topicOrQueue,
        @JsonProperty("payload_structure") @JsonPropertyDescription("Expected event payload structure or type (e.g. 'OrderCreatedEvent', 'String message').")
        String payloadStructure
    ) {}

    public record ScheduledTrigger(
        @JsonProperty("schedule_expression") @JsonPropertyDescription("Cron expression or fixed-rate/fixed-delay string (e.g. '0 0 8 * * ?', '60000').")
        String scheduleExpression,
        @JsonPropertyDescription("What this scheduled job does.")
        String description
    ) {}

    public record Outbound(
        @JsonProperty("http_calls") @JsonPropertyDescription("HTTP calls this class makes to external services (RestTemplate, WebClient, Feign, etc.).")
        List<HttpCall> httpCalls,
        @JsonProperty("event_publications") @JsonPropertyDescription("Events this class publishes to message brokers.")
        List<EventPublication> eventPublications
    ) {}

    public record HttpCall(
        @JsonPropertyDescription("HTTP method used (GET, POST, PUT, DELETE, etc.).")
        String method,
        @JsonProperty("url_or_path") @JsonPropertyDescription("URL or path of the outbound call (e.g. 'https://payment.example.com/api/charges').")
        String urlOrPath,
        @JsonProperty("encapsulated_in") @JsonPropertyDescription("The method or class that wraps this HTTP call (e.g. 'paymentService.charge()').")
        String encapsulatedIn,
        @JsonProperty("is_external") @JsonPropertyDescription("Whether the target is an external (third-party) service versus an internal microservice.")
        boolean isExternal,
        @JsonProperty("external_contract_hint") @JsonPropertyDescription("A hint about the external service's contract/API (e.g. 'REST: expects ChargeResponse', 'SOAP: expects PaymentConfirmation').")
        String externalContractHint
    ) {}

    public record EventPublication(
        @JsonPropertyDescription("Message broker type (e.g. 'kafka', 'rabbitmq', 'jms', 'activemq').")
        String broker,
        @JsonProperty("topic_or_queue") @JsonPropertyDescription("Topic or queue name where events are published.")
        String topicOrQueue,
        @JsonProperty("routing_key") @JsonPropertyDescription("Routing key used (if applicable, otherwise empty string).")
        String routingKey,
        @JsonProperty("business_trigger") @JsonPropertyDescription("The business event or condition that triggers this publication (e.g. 'Order placed', 'Payment completed').")
        String businessTrigger
    ) {}

    public record DiscoveredDependency(
        @JsonProperty("file_path") @JsonPropertyDescription("Path to the discovered dependency file (relative to project root).")
        String filePath,
        @JsonPropertyDescription("Why this dependency was discovered (e.g. 'Reflection call to createInstance()', 'SPI provider lookup').")
        String reason,
        @JsonProperty("discovery_depth") @JsonPropertyDescription("How many hops from the original task file this dependency was found (0 = direct reference, 1+ = transitive).")
        int discoveryDepth
    ) {}
}
