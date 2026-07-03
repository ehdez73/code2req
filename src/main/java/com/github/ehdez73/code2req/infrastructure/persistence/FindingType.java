package com.github.ehdez73.code2req.infrastructure.persistence;

public final class FindingType {
    public static final String COMPONENT = "COMPONENT";
    public static final String ENDPOINT = "ENDPOINT";
    public static final String SCHEDULED_TASK = "SCHEDULED_TASK";
    public static final String EVENT_LISTENER = "EVENT_LISTENER";
    public static final String EVENT_PUBLISHER = "EVENT_PUBLISHER";
    public static final String VALIDATOR = "VALIDATOR";
    public static final String KAFKA_LISTENER = "KAFKA_LISTENER";
    public static final String KAFKA_PUBLISHER = "KAFKA_PUBLISHER";
    public static final String BEAN_METHOD = "BEAN_METHOD";
    public static final String RABBITMQ_LISTENER = "RABBITMQ_LISTENER";
    public static final String RABBITMQ_PUBLISHER = "RABBITMQ_PUBLISHER";
    public static final String ACTIVEMQ_LISTENER = "ACTIVEMQ_LISTENER";
    public static final String ACTIVEMQ_PUBLISHER = "ACTIVEMQ_PUBLISHER";
    public static final String XML_BEAN = "XML_BEAN";
    public static final String DB_ACCESS = "DB_ACCESS";
    public static final String XML_COMPONENT_SCAN = "XML_COMPONENT_SCAN";
    public static final String XML_AOP_CONFIG = "XML_AOP_CONFIG";
    public static final String XML_NAMESPACE_BEAN = "XML_NAMESPACE_BEAN";
    public static final String CALL_GRAPH_EDGE = "CALL_GRAPH_EDGE";
    public static final String OUTBOUND_HTTP_CALL = "OUTBOUND_HTTP_CALL";
    public static final String TEMPLATE_FORM = "TEMPLATE_FORM";
    public static final String TEMPLATE_LINK = "TEMPLATE_LINK";
    public static final String TEMPLATE_ENDPOINT_LINK = "TEMPLATE_ENDPOINT_LINK";

    // Phase 2 Executor — semantic enrichment finding type
    public static final String SEMANTIC_ENRICHMENT = "SEMANTIC_ENRICHMENT";

    // Phase 3 caching — per-flow analysis LLM result
    public static final String FLOW_ANALYSIS = "FLOW_ANALYSIS";

    // Phase 2 Planner — granular finding types for qualification
    public static final String SPRING_DATA_INTERFACE = "SPRING_DATA_INTERFACE";
    public static final String DATABASE_PROCEDURE_CALL = "DATABASE_PROCEDURE_CALL";
    public static final String CONSTRAINT_VALIDATOR = "CONSTRAINT_VALIDATOR";
    public static final String NATIVE_SQL_QUERY = "NATIVE_SQL_QUERY";
    public static final String JPQL_HQL_QUERY = "JPQL_HQL_QUERY";

    private FindingType() {}
}
