package com.github.ehdez73.code2req.service;

import com.github.ehdez73.code2req.model.ScanTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class FilePathResolver {

    private static final Logger log = LoggerFactory.getLogger(FilePathResolver.class);

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
        Path absolute = Path.of(rawPath);
        if (absolute.isAbsolute()) {
            if (Files.isRegularFile(absolute)) {
                return Optional.of(absolute.normalize());
            }
            return Optional.empty();
        }

        String normalised = rawPath.replace('\\', '/');

        Optional<Path> byExact = findUnder(targetRoot, normalised);
        if (byExact.isPresent()) return byExact;

        if (!normalised.endsWith(".java")) {
            Optional<Path> byExactWithExt = findUnder(targetRoot, normalised + ".java");
            if (byExactWithExt.isPresent()) return byExactWithExt;
        }

        String dotToSlash = normalised.replace('.', '/');
        Optional<Path> byDots = findUnder(targetRoot, dotToSlash);
        if (byDots.isPresent()) return byDots;

        if (!dotToSlash.endsWith(".java")) {
            Optional<Path> byDotsWithExt = findUnder(targetRoot, dotToSlash + ".java");
            if (byDotsWithExt.isPresent()) return byDotsWithExt;
        }

        Optional<Path> byFilename = findFileByAnyName(targetRoot, normalised);
        if (byFilename.isPresent()) return byFilename;

        return Optional.empty();
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
