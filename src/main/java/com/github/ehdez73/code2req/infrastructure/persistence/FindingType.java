package com.github.ehdez73.code2req.infrastructure.persistence;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.java.BeanMethodInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlAopConfigInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlBeanInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlComponentScanInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlJmsListenerInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlNamespaceBeanInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlScheduledTaskInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.activemq.ActiveMqInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.activemq.ActiveMqPublisherInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.kafka.KafkaInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.kafka.KafkaPublisherInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.rabbitmq.RabbitMqInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.rabbitmq.RabbitMqPublisherInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.listener.EventListenerInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.listener.EventPublisherInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.OutboundHttpCallInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.scheduledtask.ScheduledTaskInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.validator.ValidatorInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;

import java.util.Map;

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
    public static final String XML_SCHEDULED_TASK = "XML_SCHEDULED_TASK";
    public static final String XML_JMS_LISTENER = "XML_JMS_LISTENER";
    public static final String CALL_GRAPH_EDGE = "CALL_GRAPH_EDGE";
    public static final String OUTBOUND_HTTP_CALL = "OUTBOUND_HTTP_CALL";
    public static final String TEMPLATE_FORM = "TEMPLATE_FORM";
    public static final String TEMPLATE_LINK = "TEMPLATE_LINK";
    public static final String TEMPLATE_ENDPOINT_LINK = "TEMPLATE_ENDPOINT_LINK";

    // Phase 2 Executor — semantic enrichment finding type
    public static final String SEMANTIC_ENRICHMENT = "SEMANTIC_ENRICHMENT";

    // Phase 3 caching — per-flow analysis LLM result
    public static final String FLOW_ANALYSIS = "FLOW_ANALYSIS";

    // Phase 3 caching — flow grouping LLM result
    public static final String FLOW_GROUPING = "FLOW_GROUPING";

    // Phase 2 Planner — granular finding types for qualification
    public static final String SPRING_DATA_INTERFACE = "SPRING_DATA_INTERFACE";
    public static final String DATABASE_PROCEDURE_CALL = "DATABASE_PROCEDURE_CALL";
    public static final String CONSTRAINT_VALIDATOR = "CONSTRAINT_VALIDATOR";
    public static final String NATIVE_SQL_QUERY = "NATIVE_SQL_QUERY";
    public static final String JPQL_HQL_QUERY = "JPQL_HQL_QUERY";

    public static final Map<Class<? extends AnalysisFinding>, String> FINDING_TYPE_MAP = Map.ofEntries(
        Map.entry(ComponentInfo.class, COMPONENT),
        Map.entry(EndpointInfo.class, ENDPOINT),
        Map.entry(ScheduledTaskInfo.class, SCHEDULED_TASK),
        Map.entry(EventListenerInfo.class, EVENT_LISTENER),
        Map.entry(EventPublisherInfo.class, EVENT_PUBLISHER),
        Map.entry(ValidatorInfo.class, VALIDATOR),
        Map.entry(KafkaInfo.class, KAFKA_LISTENER),
        Map.entry(KafkaPublisherInfo.class, KAFKA_PUBLISHER),
        Map.entry(BeanMethodInfo.class, BEAN_METHOD),
        Map.entry(RabbitMqInfo.class, RABBITMQ_LISTENER),
        Map.entry(RabbitMqPublisherInfo.class, RABBITMQ_PUBLISHER),
        Map.entry(ActiveMqInfo.class, ACTIVEMQ_LISTENER),
        Map.entry(ActiveMqPublisherInfo.class, ACTIVEMQ_PUBLISHER),
        Map.entry(XmlBeanInfo.class, XML_BEAN),
        Map.entry(DbAccessInfo.class, DB_ACCESS),
        Map.entry(XmlComponentScanInfo.class, XML_COMPONENT_SCAN),
        Map.entry(XmlAopConfigInfo.class, XML_AOP_CONFIG),
        Map.entry(XmlNamespaceBeanInfo.class, XML_NAMESPACE_BEAN),
        Map.entry(XmlScheduledTaskInfo.class, XML_SCHEDULED_TASK),
        Map.entry(XmlJmsListenerInfo.class, XML_JMS_LISTENER),
        Map.entry(CallGraphEdge.class, CALL_GRAPH_EDGE),
        Map.entry(OutboundHttpCallInfo.class, OUTBOUND_HTTP_CALL)
    );

    private FindingType() {}
}
