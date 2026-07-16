package com.github.ehdez73.code2req.enrichment.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class BeanDefinitionResolver {

    private static final Logger log = LoggerFactory.getLogger(BeanDefinitionResolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExecutionFindingStore executionFindingStore;

    public BeanDefinitionResolver(ExecutionFindingStore executionFindingStore) {
        this.executionFindingStore = executionFindingStore;
    }

    public Map<String, String> buildRegistry() {
        Map<String, String> registry = new HashMap<>();

        registry.putAll(extractXmlBeans());
        registry.putAll(extractBeanMethods());
        registry.putAll(extractComponents());

        return registry;
    }

    private Map<String, String> extractXmlBeans() {
        Map<String, String> result = new HashMap<>();
        var rows = executionFindingStore.findAllByType(FindingType.XML_BEAN);
        for (var row : rows) {
            String json = (String) row.get("finding_json");
            if (json == null) continue;
            try {
                JsonNode node = MAPPER.readTree(json);
                String beanId = node.has("beanId") ? node.get("beanId").asText() : null;
                String className = node.has("className") ? node.get("className").asText() : null;
                if (beanId != null && !beanId.isEmpty() && className != null && !className.isEmpty()) {
                    result.put(beanId, className);
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse XML_BEAN finding JSON: {}", e.getMessage());
            }
        }
        return result;
    }

    private Map<String, String> extractBeanMethods() {
        Map<String, String> result = new HashMap<>();
        var rows = executionFindingStore.findAllByType(FindingType.BEAN_METHOD);
        for (var row : rows) {
            String json = (String) row.get("finding_json");
            if (json == null) continue;
            try {
                JsonNode node = MAPPER.readTree(json);
                String beanName = node.has("beanName") ? node.get("beanName").asText() : null;
                String returnType = node.has("returnType") ? node.get("returnType").asText() : null;
                if (beanName != null && !beanName.isEmpty() && returnType != null && !returnType.isEmpty()) {
                    result.put(beanName, returnType);
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse BEAN_METHOD finding JSON: {}", e.getMessage());
            }
        }
        return result;
    }

    private Map<String, String> extractComponents() {
        Map<String, String> result = new HashMap<>();
        var rows = executionFindingStore.findAllByType(FindingType.COMPONENT);
        for (var row : rows) {
            String json = (String) row.get("finding_json");
            if (json == null) continue;
            try {
                JsonNode node = MAPPER.readTree(json);
                String className = node.has("className") ? node.get("className").asText() : null;
                if (className != null && !className.isEmpty() && !className.contains("$")) {
                    String simpleName = className.contains(".")
                        ? className.substring(className.lastIndexOf('.') + 1)
                        : className;
                    String beanId = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
                    result.putIfAbsent(beanId, className);
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse COMPONENT finding JSON: {}", e.getMessage());
            }
        }
        return result;
    }
}
