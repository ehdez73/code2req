package com.github.ehdez73.code2req.extraction.domain.model;

import java.util.List;

public record GherkinScenario(
    String scenarioId,
    String name,
    List<String> givenSteps,
    List<String> whenSteps,
    List<String> thenSteps,
    String sourceFlow
) {}
