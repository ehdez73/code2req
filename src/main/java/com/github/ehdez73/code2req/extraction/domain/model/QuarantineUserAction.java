package com.github.ehdez73.code2req.extraction.domain.model;

import java.util.List;

public enum QuarantineUserAction {
    ACCEPT("Accept and quarantine"),
    PROVIDE_CONTEXT("Provide additional context for the LLM"),
    DISMISS("Dismiss — flow is correct as-is");

    private final String label;

    QuarantineUserAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static List<String> options() {
        return List.of(ACCEPT.label, PROVIDE_CONTEXT.label, DISMISS.label);
    }

    public static QuarantineUserAction fromLabel(String label) {
        for (QuarantineUserAction action : values()) {
            if (action.label.equals(label)) {
                return action;
            }
        }
        return null;
    }
}
