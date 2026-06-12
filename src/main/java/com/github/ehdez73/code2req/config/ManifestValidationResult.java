package com.github.ehdez73.code2req.config;

import java.util.ArrayList;
import java.util.List;

public class ManifestValidationResult {
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public void addError(String error) {
        errors.add(error);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public List<String> getErrors() {
        return List.copyOf(errors);
    }

    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }

    public String summary() {
        if (errors.isEmpty()) {
            if (warnings.isEmpty()) {
                return "Validation passed";
            }
            return "Validation passed with " + warnings.size() + " warning(s)";
        }
        return "Validation failed with " + errors.size() + " error(s)";
    }
}
