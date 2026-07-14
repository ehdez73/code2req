package com.github.ehdez73.code2req.generation.domain.model;

import java.nio.file.Path;

public record SpecResult(
    Path markdownPath,
    Path manifestPath,
    int featureCount,
    int flowCount
) {}
