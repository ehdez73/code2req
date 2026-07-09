package com.github.ehdez73.code2req.indexing.domain.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration for package prefixes that are excluded from call graph tracing.
 * Types whose fully-qualified names start with any of these prefixes are treated
 * as library code (JDK or framework) and skipped during edge resolution.
 *
 * @param jdkPrefixes     Package prefixes for standard JDK types to exclude
 *                        from call graph tracing (default: java., javax., jakarta.)
 * @param frameworkPrefixes Package prefixes for framework/library types to exclude
 *                          from call graph tracing (default: org.springframework.,
 *                          org.hibernate., org.slf4j., com.fasterxml.jackson.,
 *                          org.apache.commons., lombok.)
 */
@ConfigurationProperties(prefix = "code2req.allowed-libraries")
public record AllowedLibrariesConfig(
    List<String> jdkPrefixes,
    List<String> frameworkPrefixes
) {
    public static final List<String> DEFAULT_JDK_PREFIXES = List.of("java.", "javax.", "jakarta.");

    public static final List<String> DEFAULT_FRAMEWORK_PREFIXES = List.of(
        "org.springframework.", "org.hibernate.", "org.slf4j.",
        "com.fasterxml.jackson.", "org.apache.commons.", "lombok."
    );

    public List<String> resolvedJdkPrefixes() {
        return jdkPrefixes != null && !jdkPrefixes.isEmpty() ? jdkPrefixes : DEFAULT_JDK_PREFIXES;
    }

    public List<String> resolvedFrameworkPrefixes() {
        return frameworkPrefixes != null && !frameworkPrefixes.isEmpty() ? frameworkPrefixes : DEFAULT_FRAMEWORK_PREFIXES;
    }
}
