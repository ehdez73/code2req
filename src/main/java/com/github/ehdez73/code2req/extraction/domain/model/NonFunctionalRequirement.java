package com.github.ehdez73.code2req.extraction.domain.model;

public record NonFunctionalRequirement(
    String category,
    String requirement,
    String sourceFile
) {}
