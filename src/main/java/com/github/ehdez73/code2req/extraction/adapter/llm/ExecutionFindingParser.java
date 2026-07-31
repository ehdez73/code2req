package com.github.ehdez73.code2req.extraction.adapter.llm;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFinding;
import org.springframework.stereotype.Component;

@Component
public class ExecutionFindingParser {

    private static final ObjectMapper LENIENT_MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, false)
        .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
        .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);

    public String sanitize(String response) {
        String s = response.trim();

        s = extractJsonSpan(s);

        if (!s.isEmpty() && s.charAt(0) != '{') {
            if (s.matches("^['\"]?\\w+['\"]?\\s*:.*")) {
                s = "{" + s + "}";
            }
        }

        s = normalizePropertyQuotes(s);

        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (start > 0 && end > start) {
                s = s.substring(start, end).trim();
            } else if (start > 0) {
                s = s.substring(start).trim();
            }
        }

        s = s.replaceAll(",\\s*}", "}").replaceAll(",\\s*]", "]");

        // Fix spurious quotes before object/array literals in array context
        // LLM sometimes emits e.g. },"{ instead of },{  or  ],"[ instead of ],[
        s = s.replaceAll("}\"\\s*\\{", "}{");
        s = s.replaceAll("\\]\"\\s*\\[", "][");
        s = s.replaceAll("}\"\\s*\\[", "}[");
        s = s.replaceAll("\\]\"\\s*\\{", "]{");

        // Fix missing commas between adjacent object/array literals (LLM sometimes omits them)
        s = fixMissingCommas(s);

        s = fixBraceBalance(s);

        return s;
    }

    public String normalize(String json) {
        try {
            JsonNode tree = LENIENT_MAPPER.readTree(json);
            if (tree instanceof ObjectNode root) {
                ensureDefaults(root);
            }
            return LENIENT_MAPPER.writeValueAsString(tree);
        } catch (Exception e) {
            return json;
        }
    }

    public ExecutionFinding parseLenient(String json) {
        try {
            JsonNode tree = LENIENT_MAPPER.readTree(json);
            if (tree instanceof ObjectNode root) {
                ensureDefaults(root);
            }
            return LENIENT_MAPPER.treeToValue(tree, ExecutionFinding.class);
        } catch (Exception e) {
            throw new RuntimeException("Lenient JSON parsing also failed", e);
        }
    }

    private String extractJsonSpan(String s) {
        int firstBrace = -1;
        int lastClose = -1;
        int depth = 0;
        boolean inString = false;
        char stringChar = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == stringChar || (c == '"' || c == '\'')) {
                    inString = false;
                    stringChar = 0;
                }
            } else {
                if (c == '"' || c == '\'') {
                    inString = true;
                    stringChar = c;
                } else if (c == '{') {
                    if (firstBrace < 0) {
                        firstBrace = i;
                    }
                    depth++;
                } else if (c == '}') {
                    if (firstBrace >= 0) {
                        depth--;
                        if (depth == 0) {
                            lastClose = i;
                        }
                    }
                }
            }
        }
        if (firstBrace >= 0 && lastClose >= 0) {
            String prefix = s.substring(0, firstBrace).trim();
            if (!prefix.isEmpty() && prefix.matches("^['\"]?\\w+['\"]?\\s*:$")) {
                firstBrace = s.indexOf(prefix);
            }
            return s.substring(firstBrace, lastClose + 1);
        }
        if (firstBrace >= 0 && depth > 0) {
            String span = s.substring(firstBrace);
            return span + "\n" + "}".repeat(depth);
        }
        return s;
    }

    private String normalizePropertyQuotes(String json) {
        String result = json.replaceAll("'(\\w+)'(\\s*:)", "\"$1\"$2");
        result = result.replaceAll("'(\\w+)\"(\\s*:)", "\"$1\"$2");
        return result;
    }

    private String fixBraceBalance(String json) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
            }
        }
        if (depth > 0) {
            return json + "\n" + "}".repeat(depth);
        }
        return json;
    }

    private String fixMissingCommas(String json) {
        StringBuilder sb = new StringBuilder(json.length() + 16);
        boolean inString = false;
        char lastNonWs = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') {
                    sb.append(c);
                    i++;
                    if (i < json.length()) sb.append(json.charAt(i));
                    continue;
                } else if (c == '"') {
                    inString = false;
                }
                sb.append(c);
            } else {
                if (c == '"') {
                    inString = true;
                    sb.append(c);
                } else {
                    if ((lastNonWs == '}' || lastNonWs == ']') && (c == '{' || c == '[')) {
                        sb.append(',');
                    }
                    sb.append(c);
                }
                if (!Character.isWhitespace(c)) {
                    lastNonWs = c;
                }
            }
        }
        return sb.toString();
    }

    private void ensureDefaults(ObjectNode root) {
        ObjectNode meta = ensureObject(root, "metadata");
        if (root.has("timestamp")) {
            meta.put("timestamp", root.get("timestamp").asText());
            root.remove("timestamp");
        }
        setStringDefault(meta, "task_id", "");
        setStringDefault(meta, "target_name", "");
        setStringDefault(meta, "file_path", "");
        setStringDefault(meta, "tech_profile", "");
        setStringDefault(meta, "module_tag", "");
        if (!meta.has("timestamp")) {
            meta.put("timestamp", "");
        }

        ObjectNode br = ensureObject(root, "business_rules_and_guardrails");
        ensureArray(br, "validations");
        ensureArray(br, "edge_cases");

        ensureArray(root, "test_insights");

        ObjectNode ac = ensureObject(root, "architectural_connections");
        ObjectNode inbound = ensureObject(ac, "inbound");
        ensureArray(inbound, "http_endpoints");
        ensureArray(inbound, "event_subscriptions");
        ensureArray(inbound, "scheduled_triggers");
        ObjectNode outbound = ensureObject(ac, "outbound");
        ensureArray(outbound, "http_calls");
        ensureArray(outbound, "event_publications");

        ensureArray(root, "discovered_dependencies");
        normalizeDiscoveredDependencies(root);
    }

    private static ObjectNode ensureObject(ObjectNode parent, String fieldName) {
        JsonNode node = parent.get(fieldName);
        if (node != null && node.isObject()) {
            return (ObjectNode) node;
        }
        ObjectNode obj = parent.objectNode();
        parent.set(fieldName, obj);
        return obj;
    }

    private static void ensureArray(ObjectNode parent, String fieldName) {
        JsonNode node = parent.get(fieldName);
        if (node != null && node.isArray()) {
            return;
        }
        parent.set(fieldName, parent.arrayNode());
    }

    private static void setStringDefault(ObjectNode node, String field, String defaultValue) {
        if (!node.has(field) || node.get(field).isNull()) {
            node.put(field, defaultValue);
        }
    }

    private static void normalizeDiscoveredDependencies(ObjectNode root) {
        JsonNode arr = root.get("discovered_dependencies");
        if (arr == null || !arr.isArray()) return;
        ArrayNode normalized = root.arrayNode();
        for (JsonNode elem : arr) {
            if (elem instanceof TextNode text) {
                ObjectNode obj = normalized.objectNode();
                obj.put("file_path", text.asText());
                obj.put("reason", "discovered during enrichment");
                obj.put("discovery_depth", 0);
                normalized.add(obj);
            } else {
                normalized.add(elem);
            }
        }
        root.set("discovered_dependencies", normalized);
    }
}
