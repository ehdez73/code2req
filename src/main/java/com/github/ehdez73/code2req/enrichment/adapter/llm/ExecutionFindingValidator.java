package com.github.ehdez73.code2req.enrichment.adapter.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Set;

@Component
public class ExecutionFindingValidator {

    private static final Logger log = LoggerFactory.getLogger(ExecutionFindingValidator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonSchema schema;

    public ExecutionFindingValidator() {
        try {
            ClassPathResource resource = new ClassPathResource("schema/execution-finding-schema.json");
            try (InputStream is = resource.getInputStream()) {
                JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
                this.schema = factory.getSchema(is);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load execution-finding-schema.json", e);
        }
    }

    public Set<ValidationMessage> validate(String json) {
        try {
            JsonNode jsonNode = MAPPER.readTree(json);
            return schema.validate(jsonNode);
        } catch (Exception e) {
            log.error("Failed to parse JSON for validation: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JSON input for schema validation", e);
        }
    }

    public boolean isValid(String json) {
        return validate(json).isEmpty();
    }
}
