package com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class ResourcePathResolver {

    private static final Logger log = LoggerFactory.getLogger(ResourcePathResolver.class);
    private static final String CLASSPATH_ALL = "classpath*:";
    private static final String CLASSPATH = "classpath:";
    private static final String FILE_PREFIX = "file:";

    private ResourcePathResolver() {}

    public static List<Path> resolve(String resourcePath, Path sourceRoot, Path relativeTo) {
        String path = resourcePath;
        boolean classpathAll = false;
        boolean isFileAbsolute = false;

        if (path.startsWith(CLASSPATH_ALL)) {
            path = path.substring(CLASSPATH_ALL.length());
            classpathAll = true;
        } else if (path.startsWith(CLASSPATH)) {
            path = path.substring(CLASSPATH.length());
        } else if (path.startsWith(FILE_PREFIX)) {
            path = path.substring(FILE_PREFIX.length());
            isFileAbsolute = true;
        }

        if (path.startsWith("/")) {
            if (isFileAbsolute) {
                Path resolved = Path.of(path).normalize();
                return Files.exists(resolved) ? List.of(resolved) : List.of();
            }
            // Leading / in classpath: paths means "relative to classpath root"
            path = path.substring(1);
        }

        List<Path> baseDirs = new ArrayList<>();
        if (sourceRoot != null && !sourceRoot.toString().isEmpty()) {
            baseDirs.add(sourceRoot.normalize());
            baseDirs.add(sourceRoot.resolve("src/main/resources").normalize());
            baseDirs.add(sourceRoot.resolve("src/test/resources").normalize());
        }
        if (relativeTo != null) {
            baseDirs.add(relativeTo.normalize());
        }

        if (classpathAll || hasGlobChars(path)) {
            return resolveGlob(path, baseDirs);
        } else {
            return resolveExact(path, baseDirs);
        }
    }

    private static List<Path> resolveExact(String path, List<Path> baseDirs) {
        Set<Path> seen = new HashSet<>();
        List<Path> matches = new ArrayList<>();
        for (Path base : baseDirs) {
            Path resolved = base.resolve(path).normalize();
            if (Files.exists(resolved) && seen.add(resolved)) {
                matches.add(resolved);
            }
        }
        return matches;
    }

    private static List<Path> resolveGlob(String path, List<Path> baseDirs) {
        if (!hasGlobChars(path)) {
            return resolveExact(path, baseDirs);
        }

        // Split at the last directory separator before the first glob character
        String prefix;
        String globPattern;
        int lastSep = path.lastIndexOf('/', findFirstGlobChar(path));
        if (lastSep >= 0) {
            prefix = path.substring(0, lastSep + 1);
            globPattern = path.substring(lastSep + 1);
        } else {
            prefix = "";
            globPattern = path;
        }

        Set<Path> matches = new HashSet<>();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);

        for (Path base : baseDirs) {
            Path searchDir = base.resolve(prefix).normalize();
            if (!Files.isDirectory(searchDir)) continue;

            try (Stream<Path> walk = Files.walk(searchDir)) {
                walk.filter(Files::isRegularFile)
                    .filter(f -> matcher.matches(searchDir.relativize(f)))
                    .forEach(matches::add);
            } catch (IOException e) {
                log.warn("Failed to walk directory {} for glob resolution: {}", searchDir, e.getMessage());
            }
        }

        return new ArrayList<>(matches);
    }

    static int findFirstGlobChar(String path) {
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '*' || c == '?' || c == '[') {
                return i;
            }
        }
        return -1;
    }

    static boolean hasGlobChars(String path) {
        return findFirstGlobChar(path) >= 0;
    }
}
