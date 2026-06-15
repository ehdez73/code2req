package com.github.ehdez73.code2req.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.ehdez73.code2req.analyzer.AnalysisFinding;
import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.activemq.ActiveMqInfo;
import com.github.ehdez73.code2req.analyzer.activemq.ActiveMqPublisherInfo;
import com.github.ehdez73.code2req.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.analyzer.component.BeanMethodInfo;
import com.github.ehdez73.code2req.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.analyzer.component.ComponentInfo;
import com.github.ehdez73.code2req.analyzer.endpoint.EndpointInfo;
import com.github.ehdez73.code2req.analyzer.eventlink.TopicLink;
import com.github.ehdez73.code2req.analyzer.eventlistener.EventListenerInfo;
import com.github.ehdez73.code2req.analyzer.eventlistener.EventPublisherInfo;
import com.github.ehdez73.code2req.analyzer.kafka.KafkaInfo;
import com.github.ehdez73.code2req.analyzer.kafka.KafkaPublisherInfo;
import com.github.ehdez73.code2req.analyzer.rabbitmq.RabbitMqInfo;
import com.github.ehdez73.code2req.analyzer.rabbitmq.RabbitMqPublisherInfo;
import com.github.ehdez73.code2req.analyzer.scheduledtask.ScheduledTaskInfo;
import com.github.ehdez73.code2req.analyzer.validator.ValidatorInfo;
import com.github.ehdez73.code2req.analyzer.xml.XmlAopConfigInfo;
import com.github.ehdez73.code2req.analyzer.xml.XmlBeanInfo;
import com.github.ehdez73.code2req.analyzer.xml.XmlComponentScanInfo;
import com.github.ehdez73.code2req.analyzer.xml.XmlNamespaceBeanInfo;
import com.github.ehdez73.code2req.model.OutputConfig;
import com.github.ehdez73.code2req.model.ProjectManifest;
import com.github.ehdez73.code2req.model.ScanTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IndexWriter {
    private static final Logger log = LoggerFactory.getLogger(IndexWriter.class);
    private static final Map<Class<? extends AnalysisFinding>, String> FINDING_KEYS = new LinkedHashMap<>();

    static {
        FINDING_KEYS.put(ComponentInfo.class, "components");
        FINDING_KEYS.put(EndpointInfo.class, "endpoints");
        FINDING_KEYS.put(ScheduledTaskInfo.class, "scheduled_tasks");
        FINDING_KEYS.put(EventListenerInfo.class, "event_listeners");
        FINDING_KEYS.put(EventPublisherInfo.class, "event_publishers");
        FINDING_KEYS.put(ValidatorInfo.class, "validators");
        FINDING_KEYS.put(KafkaInfo.class, "kafka_listeners");
        FINDING_KEYS.put(KafkaPublisherInfo.class, "kafka_publishers");
        FINDING_KEYS.put(BeanMethodInfo.class, "bean_methods");
        FINDING_KEYS.put(RabbitMqInfo.class, "rabbitmq_listeners");
        FINDING_KEYS.put(RabbitMqPublisherInfo.class, "rabbitmq_publishers");
        FINDING_KEYS.put(ActiveMqInfo.class, "activemq_listeners");
        FINDING_KEYS.put(ActiveMqPublisherInfo.class, "activemq_publishers");
        FINDING_KEYS.put(XmlBeanInfo.class, "xml_beans");
        FINDING_KEYS.put(DbAccessInfo.class, "database_access");
        FINDING_KEYS.put(XmlComponentScanInfo.class, "xml_component_scans");
        FINDING_KEYS.put(XmlAopConfigInfo.class, "xml_aop_configs");
        FINDING_KEYS.put(XmlNamespaceBeanInfo.class, "xml_namespace_beans");
        FINDING_KEYS.put(CallGraphEdge.class, "call_graph_edges");
    }

    private final ObjectMapper mapper;

    public IndexWriter() {
        this.mapper = new ObjectMapper();
    }

    public Path write(ProjectManifest manifest, List<AnalysisResult> results) throws IOException {
        return write(manifest, results, List.of());
    }

    public Path write(ProjectManifest manifest, List<AnalysisResult> results, List<TopicLink> topicLinks) throws IOException {
        OutputConfig outputConfig = manifest.outputConfig();
        Path outputDir = Path.of(outputConfig.specDir());
        Files.createDirectories(outputDir);

        if (!Files.isWritable(outputDir)) {
            throw new IOException("Output directory is not writable: " + outputDir.toAbsolutePath());
        }

        ObjectNode root = mapper.createObjectNode();
        root.put("version", "1.0");
        root.put("generated_at", LocalDateTime.now().toString());

        ArrayNode targetsArray = root.putArray("targets");
        for (ScanTarget target : manifest.targets()) {
            Path targetPath = Path.of(target.path()).normalize();
            List<AnalysisResult> targetResults = results.stream()
                .filter(r -> Path.of(r.filePath()).normalize().startsWith(targetPath))
                .toList();
            targetsArray.add(buildTargetNode(target, targetResults));
        }

        if (!topicLinks.isEmpty()) {
            root.set("topic_links", mapper.valueToTree(topicLinks));
        }

        Path outputPath = outputDir.resolve(outputConfig.indexFile());
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), root);
        log.info("Index written to {}", outputPath);
        return outputPath;
    }

    private ObjectNode buildTargetNode(ScanTarget target, List<AnalysisResult> results) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", target.name());

        for (var entry : FINDING_KEYS.entrySet()) {
            List<AnalysisFinding> collected = new ArrayList<>();
            for (AnalysisResult result : results) {
                collected.addAll(result.findings(entry.getKey()));
            }
            if (!collected.isEmpty()) {
                node.set(entry.getValue(), mapper.valueToTree(collected));
            }
        }

        return node;
    }
}
