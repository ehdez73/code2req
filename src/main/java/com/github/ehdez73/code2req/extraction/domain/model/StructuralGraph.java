package com.github.ehdez73.code2req.extraction.domain.model;

import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlJmsListenerInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlScheduledTaskInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.activemq.ActiveMqInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.kafka.KafkaInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.rabbitmq.RabbitMqInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.listener.EventListenerInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.scheduledtask.ScheduledTaskInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StructuralGraph {

    private final List<CallGraphEdge> callGraphEdges;
    private final List<EndpointInfo> endpoints;
    private final List<DbAccessInfo> dbAccessPatterns;
    private final List<ComponentInfo> components;
    private final List<ScheduledTaskInfo> scheduledTasks;
    private final List<KafkaInfo> kafkaListeners;
    private final List<RabbitMqInfo> rabbitmqListeners;
    private final List<ActiveMqInfo> activemqListeners;
    private final List<EventListenerInfo> eventListeners;
    private final List<XmlScheduledTaskInfo> xmlScheduledTasks;
    private final List<XmlJmsListenerInfo> xmlJmsListeners;
    private final List<ScheduledEntryPoint> resolvedXmlScheduledTasks;
    private final List<ActiveMqEntryPoint> resolvedXmlJmsListeners;
    private final Map<String, String> wiringMap;
    private final Map<String, String> classToFileMap;

    public StructuralGraph() {
        this.callGraphEdges = new ArrayList<>();
        this.endpoints = new ArrayList<>();
        this.dbAccessPatterns = new ArrayList<>();
        this.components = new ArrayList<>();
        this.scheduledTasks = new ArrayList<>();
        this.kafkaListeners = new ArrayList<>();
        this.rabbitmqListeners = new ArrayList<>();
        this.activemqListeners = new ArrayList<>();
        this.eventListeners = new ArrayList<>();
        this.xmlScheduledTasks = new ArrayList<>();
        this.xmlJmsListeners = new ArrayList<>();
        this.resolvedXmlScheduledTasks = new ArrayList<>();
        this.resolvedXmlJmsListeners = new ArrayList<>();
        this.wiringMap = new HashMap<>();
        this.classToFileMap = new HashMap<>();
    }

    public StructuralGraph(
            List<CallGraphEdge> callGraphEdges,
            List<EndpointInfo> endpoints,
            List<DbAccessInfo> dbAccessPatterns,
            List<ComponentInfo> components) {
        this(callGraphEdges, endpoints, dbAccessPatterns, components,
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>(), new HashMap<>(), new HashMap<>());
    }

    public StructuralGraph(
            List<CallGraphEdge> callGraphEdges,
            List<EndpointInfo> endpoints,
            List<DbAccessInfo> dbAccessPatterns,
            List<ComponentInfo> components,
            List<ScheduledTaskInfo> scheduledTasks,
            List<KafkaInfo> kafkaListeners,
            List<RabbitMqInfo> rabbitmqListeners,
            List<ActiveMqInfo> activemqListeners,
            List<EventListenerInfo> eventListeners,
            List<XmlScheduledTaskInfo> xmlScheduledTasks,
            List<XmlJmsListenerInfo> xmlJmsListeners,
            List<ScheduledEntryPoint> resolvedXmlScheduledTasks,
            List<ActiveMqEntryPoint> resolvedXmlJmsListeners) {
        this(callGraphEdges, endpoints, dbAccessPatterns, components,
            scheduledTasks, kafkaListeners, rabbitmqListeners,
            activemqListeners, eventListeners, xmlScheduledTasks, xmlJmsListeners,
            resolvedXmlScheduledTasks, resolvedXmlJmsListeners,
            new HashMap<>(), new HashMap<>());
    }

    public StructuralGraph(
            List<CallGraphEdge> callGraphEdges,
            List<EndpointInfo> endpoints,
            List<DbAccessInfo> dbAccessPatterns,
            List<ComponentInfo> components,
            List<ScheduledTaskInfo> scheduledTasks,
            List<KafkaInfo> kafkaListeners,
            List<RabbitMqInfo> rabbitmqListeners,
            List<ActiveMqInfo> activemqListeners,
            List<EventListenerInfo> eventListeners,
            List<XmlScheduledTaskInfo> xmlScheduledTasks,
            List<XmlJmsListenerInfo> xmlJmsListeners,
            List<ScheduledEntryPoint> resolvedXmlScheduledTasks,
            List<ActiveMqEntryPoint> resolvedXmlJmsListeners,
            Map<String, String> wiringMap,
            Map<String, String> classToFileMap) {
        this.callGraphEdges = Collections.unmodifiableList(callGraphEdges);
        this.endpoints = Collections.unmodifiableList(endpoints);
        this.dbAccessPatterns = Collections.unmodifiableList(dbAccessPatterns);
        this.components = Collections.unmodifiableList(components);
        this.scheduledTasks = Collections.unmodifiableList(scheduledTasks);
        this.kafkaListeners = Collections.unmodifiableList(kafkaListeners);
        this.rabbitmqListeners = Collections.unmodifiableList(rabbitmqListeners);
        this.activemqListeners = Collections.unmodifiableList(activemqListeners);
        this.eventListeners = Collections.unmodifiableList(eventListeners);
        this.xmlScheduledTasks = Collections.unmodifiableList(xmlScheduledTasks);
        this.xmlJmsListeners = Collections.unmodifiableList(xmlJmsListeners);
        this.resolvedXmlScheduledTasks = Collections.unmodifiableList(resolvedXmlScheduledTasks);
        this.resolvedXmlJmsListeners = Collections.unmodifiableList(resolvedXmlJmsListeners);
        this.wiringMap = wiringMap != null ? Collections.unmodifiableMap(new HashMap<>(wiringMap)) : Map.of();
        this.classToFileMap = classToFileMap != null ? Collections.unmodifiableMap(new HashMap<>(classToFileMap)) : Map.of();
    }

    public List<CallGraphEdge> callGraphEdges() { return callGraphEdges; }
    public List<EndpointInfo> endpoints() { return endpoints; }
    public List<DbAccessInfo> dbAccessPatterns() { return dbAccessPatterns; }
    public List<ComponentInfo> components() { return components; }
    public List<ScheduledTaskInfo> scheduledTasks() { return scheduledTasks; }
    public List<KafkaInfo> kafkaListeners() { return kafkaListeners; }
    public List<RabbitMqInfo> rabbitmqListeners() { return rabbitmqListeners; }
    public List<ActiveMqInfo> activemqListeners() { return activemqListeners; }
    public List<EventListenerInfo> eventListeners() { return eventListeners; }
    public List<XmlScheduledTaskInfo> xmlScheduledTasks() { return xmlScheduledTasks; }
    public List<XmlJmsListenerInfo> xmlJmsListeners() { return xmlJmsListeners; }
    public List<ScheduledEntryPoint> resolvedXmlScheduledTasks() { return resolvedXmlScheduledTasks; }
    public List<ActiveMqEntryPoint> resolvedXmlJmsListeners() { return resolvedXmlJmsListeners; }
    public Map<String, String> wiringMap() { return wiringMap; }
    public Map<String, String> classToFileMap() { return classToFileMap; }

    public List<String> getFlowCandidates() {
        return callGraphEdges.stream()
            .filter(CallGraphEdge::isResolved)
            .map(CallGraphEdge::sourceFilePath)
            .distinct()
            .collect(Collectors.toList());
    }

    public List<EndpointInfo> getEndpointsByHttpMethod(String method) {
        return endpoints.stream()
            .filter(e -> e.httpMethod().equalsIgnoreCase(method))
            .collect(Collectors.toList());
    }

    public List<CallGraphEdge> getCallersOf(String targetFile) {
        return callGraphEdges.stream()
            .filter(e -> targetFile.equals(e.targetFilePath()))
            .collect(Collectors.toList());
    }

    public List<CallGraphEdge> getCalleesOf(String sourceFile) {
        return callGraphEdges.stream()
            .filter(e -> sourceFile.equals(e.sourceFilePath()))
            .collect(Collectors.toList());
    }

    public List<ComponentInfo> getComponentsByType(String annotationType) {
        return components.stream()
            .filter(c -> annotationType.equals(c.annotationType()))
            .collect(Collectors.toList());
    }

    public List<EntryPoint> getEntryPoints() {
        Map<String, EntryPoint> deduped = new java.util.LinkedHashMap<>();

        for (EndpointInfo ep : endpoints) {
            String id = ep.filePath() + ":" + ep.className() + ":" + ep.methodName() + " " + ep.httpMethod() + " " + ep.path();
            deduped.putIfAbsent(id, new HttpEntryPoint(
                id, ep.className(), ep.methodName(), ep.filePath(),
                0.0, false,
                ep.httpMethod(), ep.path(), ep.pathVariables(), ep.requestBodies(),
                ep.startLine(), ep.endLine()
            ));
        }

        for (ScheduledTaskInfo st : scheduledTasks) {
            String schedule = st.cron() != null ? st.cron()
                : (st.fixedRate() != null ? "fixedRate=" + st.fixedRate()
                : "fixedDelay=" + st.fixedDelay());
            String id = st.filePath() + ":" + st.className() + ":" + st.methodName() + " " + schedule;
            deduped.putIfAbsent(id, new ScheduledEntryPoint(
                id, st.className(), st.methodName(), st.filePath(),
                0.0, false, schedule,
                st.startLine(), st.endLine()
            ));
        }

        for (KafkaInfo k : kafkaListeners) {
            String id = k.filePath() + ":" + k.className() + ":" + k.methodName() + " " + k.topics();
            deduped.putIfAbsent(id, new KafkaEntryPoint(
                id, k.className(), k.methodName(), k.filePath(),
                0.0, false,
                k.topics(), k.isPattern(), k.payloadType()
            ));
        }

        for (RabbitMqInfo r : rabbitmqListeners) {
            String id = r.filePath() + ":" + r.className() + ":" + r.methodName() + " " + r.queues();
            deduped.putIfAbsent(id, new RabbitMqEntryPoint(
                id, r.className(), r.methodName(), r.filePath(),
                0.0, false,
                r.queues(), r.payloadType()
            ));
        }

        for (ActiveMqInfo a : activemqListeners) {
            String id = a.filePath() + ":" + a.className() + ":" + a.methodName() + " " + a.destination();
            deduped.putIfAbsent(id, new ActiveMqEntryPoint(
                id, a.className(), a.methodName(), a.filePath(),
                0.0, false,
                a.destination(), a.payloadType()
            ));
        }

        for (EventListenerInfo el : eventListeners) {
            String id = el.filePath() + ":" + el.className() + ":" + el.methodName() + " " + el.payLoadType();
            deduped.putIfAbsent(id, new EventListenerEntryPoint(
                id, el.className(), el.methodName(), el.filePath(),
                0.0, false,
                el.payLoadType()
            ));
        }

        for (ScheduledEntryPoint sep : resolvedXmlScheduledTasks) {
            deduped.putIfAbsent(sep.id(), sep);
        }
        for (ActiveMqEntryPoint amep : resolvedXmlJmsListeners) {
            deduped.putIfAbsent(amep.id(), amep);
        }

        return new ArrayList<>(deduped.values());
    }

    public List<MethodIdentifier> getAllKnownMethods() {
        List<MethodIdentifier> methods = new ArrayList<>();

        for (ScheduledTaskInfo st : scheduledTasks) {
            methods.add(new MethodIdentifier(st.className(), st.methodName(), st.filePath()));
        }

        for (KafkaInfo k : kafkaListeners) {
            methods.add(new MethodIdentifier(k.className(), k.methodName(), k.filePath()));
        }

        for (RabbitMqInfo r : rabbitmqListeners) {
            methods.add(new MethodIdentifier(r.className(), r.methodName(), r.filePath()));
        }

        for (ActiveMqInfo a : activemqListeners) {
            methods.add(new MethodIdentifier(a.className(), a.methodName(), a.filePath()));
        }

        for (EventListenerInfo el : eventListeners) {
            methods.add(new MethodIdentifier(el.className(), el.methodName(), el.filePath()));
        }

        for (ScheduledEntryPoint sep : resolvedXmlScheduledTasks) {
            methods.add(new MethodIdentifier(sep.className(), sep.methodName(), sep.filePath()));
        }

        for (XmlJmsListenerInfo xjl : xmlJmsListeners) {
            methods.add(new MethodIdentifier(xjl.beanName(), xjl.method(), xjl.filePath()));
        }

        return methods;
    }

    public double getEntryPointPriority(
            EntryPoint entryPoint,
            SemanticEnrichment enrichment,
            Map<String, String> testFileMapping) {
        double score = 0.0;

        long downstreamCount = callGraphEdges.stream()
            .filter(e -> entryPoint.filePath().equals(e.sourceFilePath()))
            .count();
        score += 0.4 * Math.min(downstreamCount / 10.0, 1.0);

        score += 0.3 * (entryPoint instanceof HttpEntryPoint ? 1.0 : 0.5);

        boolean hasTestFile = testFileMapping.containsKey(entryPoint.filePath());
        score += 0.3 * (hasTestFile ? 1.0 : 0.0);

        return score;
    }
}
