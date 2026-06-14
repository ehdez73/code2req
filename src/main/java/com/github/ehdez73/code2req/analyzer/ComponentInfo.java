package com.github.ehdez73.code2req.analyzer;

public record ComponentInfo(
    String annotationType,
    String className,
    String packageName,
    String filePath
) {}
