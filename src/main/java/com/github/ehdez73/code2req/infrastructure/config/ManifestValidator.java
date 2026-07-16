package com.github.ehdez73.code2req.infrastructure.config;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@Service
public class ManifestValidator {
    private static final Set<String> KNOWN_TOP_LEVEL_FIELDS = Set.of("targets", "execution", "output");
    private static final Set<String> KNOWN_TARGET_FIELDS = Set.of(
        "name", "path", "layer", "tech_profile", "entry_points", "exclude_patterns", "java_version"
    );
    private static final Set<String> KNOWN_EXECUTION_FIELDS = Set.of(
        "max-concurrent-llm-calls", "max-discovery-depth", "semantic-validation-sample-rate"
    );
    private static final Set<String> KNOWN_OUTPUT_FIELDS = Set.of(
        "spec-dir"
    );

    private final ManifestLoader manifestLoader;

    public ManifestValidator(ManifestLoader manifestLoader) {
        this.manifestLoader = manifestLoader;
    }

    public ManifestValidationResult validate(Path manifestPath) throws IOException {
        ManifestValidationResult result = new ManifestValidationResult();

        if (!Files.exists(manifestPath)) {
            result.addError("Manifest file not found: " + manifestPath);
            return result;
        }

        JsonNode root;
        try {
            root = manifestLoader.loadAsJsonNode(manifestPath);
        } catch (IOException e) {
            result.addError("Failed to parse manifest YAML: " + e.getMessage());
            return result;
        }

        if (!root.isObject()) {
            result.addError("Manifest must be a YAML object at the root level");
            return result;
        }

        detectUnknownFields(root, KNOWN_TOP_LEVEL_FIELDS, result, "top-level");

        if (!root.has("targets")) {
            result.addError("Missing required field: 'targets'");
            return result;
        }

        JsonNode targets = root.get("targets");
        if (!targets.isArray()) {
            result.addError("'targets' must be an array");
            return result;
        }

        if (targets.isEmpty()) {
            result.addError("'targets' must contain at least one target");
            return result;
        }

        for (int i = 0; i < targets.size(); i++) {
            JsonNode target = targets.get(i);
            String targetName = target.has("name") ? target.get("name").asText() : "<unnamed target " + (i + 1) + ">";

            if (!target.isObject()) {
                result.addError("Target at index " + i + " must be an object");
                continue;
            }

            if (!target.has("name") || target.get("name").asText().isBlank()) {
                result.addError("Target at index " + i + " is missing required field: 'name'");
            }

            if (!target.has("path") || target.get("path").asText().isBlank()) {
                result.addError("Target '" + targetName + "' is missing required field: 'path'");
            }

            detectUnknownFields(target, KNOWN_TARGET_FIELDS, result, "target '" + targetName + "'");

            if (target.has("path") && !target.get("path").asText().isBlank()) {
                Path targetPath = Path.of(target.get("path").asText());
                if (!Files.exists(targetPath)) {
                    result.addWarning("Target '" + targetName + "' path does not exist: " + targetPath
                        + " — this target will be skipped during scan");
                } else if (!Files.isDirectory(targetPath)) {
                    result.addWarning("Target '" + targetName + "' path is not a directory: " + targetPath);
                }
            }
        }

        if (root.has("execution")) {
            JsonNode execution = root.get("execution");
            if (execution.isObject()) {
                detectUnknownFields(execution, KNOWN_EXECUTION_FIELDS, result, "'execution' section");
            }
        }

        if (root.has("output")) {
            JsonNode output = root.get("output");
            if (output.isObject()) {
                detectUnknownFields(output, KNOWN_OUTPUT_FIELDS, result, "'output' section");
            }
        }

        return result;
    }

    private void detectUnknownFields(JsonNode node, Set<String> knownFields,
                                     ManifestValidationResult result, String context) {
        var fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (!knownFields.contains(field)) {
                result.addWarning("Unrecognized field '" + field + "' in " + context);
            }
        }
    }
}
