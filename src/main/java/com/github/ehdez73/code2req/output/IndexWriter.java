package com.github.ehdez73.code2req.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.ehdez73.code2req.analyzer.AnalysisFinding;
import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.event.broker.activemq.ActiveMqInfo;
import com.github.ehdez73.code2req.analyzer.event.broker.activemq.ActiveMqPublisherInfo;
import com.github.ehdez73.code2req.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.analyzer.bean.java.BeanMethodInfo;
import com.github.ehdez73.code2req.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.analyzer.httpclient.OutboundHttpCallInfo;
import com.github.ehdez73.code2req.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.analyzer.web.endpoint.EndpointInfo;
import com.github.ehdez73.code2req.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.analyzer.event.listener.EventListenerInfo;
import com.github.ehdez73.code2req.analyzer.event.listener.EventPublisherInfo;
import com.github.ehdez73.code2req.analyzer.event.broker.kafka.KafkaInfo;
import com.github.ehdez73.code2req.analyzer.event.broker.kafka.KafkaPublisherInfo;
import com.github.ehdez73.code2req.analyzer.event.broker.rabbitmq.RabbitMqInfo;
import com.github.ehdez73.code2req.analyzer.event.broker.rabbitmq.RabbitMqPublisherInfo;
import com.github.ehdez73.code2req.analyzer.scheduledtask.ScheduledTaskInfo;
import com.github.ehdez73.code2req.analyzer.web.template.TemplateFormInfo;
import com.github.ehdez73.code2req.analyzer.web.template.TemplateLinkInfo;
import com.github.ehdez73.code2req.analyzer.validator.ValidatorInfo;
import com.github.ehdez73.code2req.analyzer.bean.xml.XmlAopConfigInfo;
import com.github.ehdez73.code2req.analyzer.bean.xml.XmlBeanInfo;
import com.github.ehdez73.code2req.analyzer.bean.xml.XmlComponentScanInfo;
import com.github.ehdez73.code2req.analyzer.bean.xml.XmlNamespaceBeanInfo;
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
        FINDING_KEYS.put(OutboundHttpCallInfo.class, "outbound_http_calls");
    }

    private final ObjectMapper mapper;
    private final OutputConfig outputConfig;

    public IndexWriter(OutputConfig outputConfig) {
        this.mapper = new ObjectMapper();
        this.outputConfig = outputConfig;
    }

    public Path write(ProjectManifest manifest, List<AnalysisResult> results) throws IOException {
        return write(manifest, results, List.of(), List.of(), List.of(), List.of());
    }

    public Path write(ProjectManifest manifest, List<AnalysisResult> results, List<TopicLink> topicLinks) throws IOException {
        return write(manifest, results, topicLinks, List.of(), List.of(), List.of());
    }

    public Path write(ProjectManifest manifest, List<AnalysisResult> results, List<TopicLink> topicLinks,
                      List<TemplateFormInfo> templateForms) throws IOException {
        return write(manifest, results, topicLinks, templateForms, List.of(), List.of());
    }

    public Path write(ProjectManifest manifest, List<AnalysisResult> results, List<TopicLink> topicLinks,
                      List<TemplateFormInfo> templateForms,
                      List<TemplateLinkInfo> templateLinks) throws IOException {
        return write(manifest, results, topicLinks, templateForms, templateLinks, List.of());
    }

    public Path write(ProjectManifest manifest, List<AnalysisResult> results, List<TopicLink> topicLinks,
                      List<TemplateFormInfo> templateForms,
                      List<TemplateLinkInfo> templateLinks,
                      List<FloatingLinkInfo> floatingLinks) throws IOException {
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
            List<TemplateFormInfo> targetForms = templateForms.stream()
                .filter(tf -> Path.of(tf.templatePath()).normalize().startsWith(targetPath))
                .toList();
            targetsArray.add(buildTargetNode(target, targetResults, targetForms));
        }

        if (!topicLinks.isEmpty()) {
            root.set("topic_links", mapper.valueToTree(topicLinks));
        }

        if (!templateLinks.isEmpty()) {
            root.set("template_endpoint_links", mapper.valueToTree(templateLinks));
        }

        if (!floatingLinks.isEmpty()) {
            root.set("floating_links", mapper.valueToTree(floatingLinks));
        }

        Path outputPath = outputDir.resolve(outputConfig.indexFile());
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), root);
        log.info("Index written to {}", outputPath);
        return outputPath;
    }

    private ObjectNode buildTargetNode(ScanTarget target, List<AnalysisResult> results) {
        return buildTargetNode(target, results, List.of());
    }

    private ObjectNode buildTargetNode(ScanTarget target, List<AnalysisResult> results, List<TemplateFormInfo> templateForms) {
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

        if (!templateForms.isEmpty()) {
            var forms = templateForms.stream().filter(f -> "FORM".equals(f.linkType())).toList();
            var links = templateForms.stream().filter(f -> "LINK".equals(f.linkType())).toList();
            if (!forms.isEmpty()) {
                node.set("template_forms", mapper.valueToTree(forms));
            }
            if (!links.isEmpty()) {
                node.set("template_anchor_links", mapper.valueToTree(links));
            }
        }

        return node;
    }
}
