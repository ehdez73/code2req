package com.github.ehdez73.code2req.extraction.adapter.agent.model;

import java.nio.file.Path;

public record SpecResult(
    Path markdownPath,
    Path manifestPath,
    int featureCount,
    int flowCount
) {}
