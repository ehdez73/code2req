package com.github.ehdez73.code2req.indexing.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcludeFilter {

    private static final Logger log = LoggerFactory.getLogger(ExcludeFilter.class);
    private static final FileSystem FS = FileSystems.getDefault();

    private static final List<String> DEFAULT_PATTERNS = List.of(
        "{,**/}target/**",
        "{,**/}build/**",
        "{,**/}generated/**",
        "{,**/}.git/**",
        "{,**/}node_modules/**",
        "{,**/}.gradle/**",
        "{,**/}.idea/**",
        "{,**/}*.class",
        "{,**/}*.jar"
    );

    public static List<String> defaultPatterns() {
        return DEFAULT_PATTERNS;
    }

    public ExcludeResult filter(Path targetRoot, List<Path> files, List<String> extraPatterns) {
        List<String> allPatterns = new ArrayList<>(DEFAULT_PATTERNS);
        if (extraPatterns != null) {
            allPatterns.addAll(extraPatterns);
        }
        return applyFilter(targetRoot, files, allPatterns);
    }

    public ExcludeResult filterWithDefaults(Path targetRoot, List<Path> files) {
        return applyFilter(targetRoot, files, DEFAULT_PATTERNS);
    }

    public boolean shouldExclude(Path targetRoot, Path file, List<String> patterns) {
        Path relative = targetRoot.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize());
        for (String pattern : patterns) {
            String normalized = normalizeGlob(pattern);
            PathMatcher matcher = FS.getPathMatcher("glob:" + normalized);
            if (matcher.matches(relative)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeGlob(String pattern) {
        if (pattern.startsWith("**/")) {
            return "{,**/}" + pattern.substring(3);
        }
        return pattern;
    }

    private ExcludeResult applyFilter(Path targetRoot, List<Path> files, List<String> patterns) {
        List<Path> included = new ArrayList<>();
        List<Path> excluded = new ArrayList<>();
        Path root = targetRoot.toAbsolutePath().normalize();

        for (Path file : files) {
            if (shouldExclude(root, file, patterns)) {
                excluded.add(file);
            } else {
                included.add(file);
            }
        }

        if (!excluded.isEmpty()) {
            log.info("Excluded {} file(s) via pattern filter", excluded.size());
        }

        return new ExcludeResult(included, excluded);
    }
}
