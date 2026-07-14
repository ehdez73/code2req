package com.github.ehdez73.code2req.infrastructure.file;

import com.github.ehdez73.code2req.common.domain.ScanTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class FilePathResolver {

    private static final Logger log = LoggerFactory.getLogger(FilePathResolver.class);

    @FunctionalInterface
    private interface ResolutionStrategy {
        Optional<Path> resolve(String path, Path targetRoot);
    }

    private final List<ResolutionStrategy> strategies;

    public FilePathResolver() {
        this.strategies = List.of(
            this::resolveAbsolute,
            this::resolveFullyQualifiedClassName,
            this::resolveExactMatch,
            this::resolveExactMatchWithExt,
            this::resolveDotsToSlashes,
            this::resolveDotsToSlashesWithExt,
            this::resolveByFilename
        );
    }

    public Optional<Path> resolve(String rawPath, Collection<ScanTarget> targets) {
        if (rawPath == null || rawPath.isBlank()) {
            return Optional.empty();
        }

        String trimmed = rawPath.strip();

        if (isWildcardOrImport(trimmed)) {
            return Optional.empty();
        }

        for (ScanTarget target : targets) {
            Path targetRoot = Path.of(target.path()).normalize();
            Optional<Path> result = resolveAgainst(trimmed, targetRoot);
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    private Optional<Path> resolveAgainst(String rawPath, Path targetRoot) {
        String path = rawPath.replace('\\', '/');
        if (path.endsWith(".class")) {
            return resolveClassFile(path, targetRoot);
        }
        for (var strategy : strategies) {
            Optional<Path> result = strategy.resolve(path, targetRoot);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    private Optional<Path> resolveClassFile(String path, Path targetRoot) {
        if (!path.endsWith(".class")) return Optional.empty();
        String base = path.substring(0, path.length() - 6);
        return findUnder(targetRoot, base + ".java")
            .or(() -> findUnder(targetRoot, base.replace('.', '/') + ".java"))
            .or(() -> findFileByAnyName(targetRoot, base + ".java"));
    }

    private Optional<Path> resolveAbsolute(String path, Path targetRoot) {
        Path absolute = Path.of(path);
        if (absolute.isAbsolute() && Files.isRegularFile(absolute)) {
            return Optional.of(absolute.normalize());
        }
        return Optional.empty();
    }

    private Optional<Path> resolveFullyQualifiedClassName(String path, Path targetRoot) {
        if (path.endsWith(".java")) return Optional.empty();
        if (!path.contains(".")) return Optional.empty();
        if (path.contains("/") || path.contains("\\")) return Optional.empty();
        int lastDot = path.lastIndexOf('.');
        String className = path.substring(lastDot + 1);
        if (className.isEmpty() || Character.isLowerCase(className.charAt(0))) {
            return Optional.empty();
        }
        return findUnder(targetRoot, path.replace('.', '/') + ".java");
    }

    private Optional<Path> resolveExactMatch(String path, Path targetRoot) {
        return findUnder(targetRoot, path);
    }

    private Optional<Path> resolveExactMatchWithExt(String path, Path targetRoot) {
        if (path.endsWith(".java")) return Optional.empty();
        return findUnder(targetRoot, path + ".java");
    }

    private Optional<Path> resolveDotsToSlashes(String path, Path targetRoot) {
        return findUnder(targetRoot, path.replace('.', '/'));
    }

    private Optional<Path> resolveDotsToSlashesWithExt(String path, Path targetRoot) {
        String converted = path.replace('.', '/');
        if (converted.endsWith(".java")) return Optional.empty();
        return findUnder(targetRoot, converted + ".java");
    }

    private Optional<Path> resolveByFilename(String path, Path targetRoot) {
        return findFileByAnyName(targetRoot, path);
    }

    private Optional<Path> findUnder(Path targetRoot, String relativePath) {
        String suffix = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        try (Stream<Path> walk = Files.walk(targetRoot)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(p -> p.toAbsolutePath().normalize().toString().endsWith(suffix))
                .findFirst()
                .map(Path::normalize);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<Path> findFileByAnyName(Path targetRoot, String name) {
        String filename = name.contains("/")
            ? name.substring(name.lastIndexOf('/') + 1)
            : name;
        String basename = filename.endsWith(".java") ? filename : filename + ".java";
        try (Stream<Path> walk = Files.walk(targetRoot)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equals(basename))
                .findFirst()
                .map(Path::normalize);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    static boolean isWildcardOrImport(String path) {
        return path.contains("*");
    }
}
