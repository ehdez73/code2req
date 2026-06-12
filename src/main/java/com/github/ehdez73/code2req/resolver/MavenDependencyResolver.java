package com.github.ehdez73.code2req.resolver;

import com.github.ehdez73.code2req.model.Dependency;
import com.github.ehdez73.code2req.model.DependencyGraph;
import com.github.ehdez73.code2req.model.ScanTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MavenDependencyResolver {

    private static final Logger log = LoggerFactory.getLogger(MavenDependencyResolver.class);
    private static final int TIMEOUT_SECONDS = 30;
    private static final Pattern DEP_COORD_PATTERN = Pattern.compile(
        "([^\\s:]+):([^\\s:]+):([^\\s:]+):([^\\s:]+)(?::([^\\s:]+))?");

    public DependencyGraph resolve(ScanTarget target) {
        if (!isMavenAvailable()) {
            log.warn("Maven is not available on PATH. "
                + "Falling back to heuristic mode. "
                + "Install Maven and ensure `mvn` is on PATH for dependency resolution.");
            return DependencyGraph.heuristic("Maven not available on PATH");
        }

        Path targetPath = Path.of(target.path());
        Path pomXml = targetPath.resolve("pom.xml");

        if (!Files.exists(pomXml)) {
            log.warn("No pom.xml found at {}. Falling back to heuristic mode.", pomXml.toAbsolutePath());
            return DependencyGraph.heuristic("No pom.xml found at " + pomXml.toAbsolutePath());
        }

        try {
            return runDependencyTree(targetPath);
        } catch (Exception e) {
            log.warn("Maven dependency resolution failed for '{}': {}. "
                    + "Falling back to heuristic mode.", target.name(), e.getMessage());
            log.debug("Maven resolution failure details", e);
            return DependencyGraph.heuristic("Maven resolution failed: " + e.getMessage());
        }
    }

    private DependencyGraph runDependencyTree(Path projectDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("mvn", "dependency:tree", "--batch-mode")
            .directory(projectDir.toFile())
            .redirectErrorStream(true);

        log.info("Running Maven dependency:tree in {}", projectDir);

        Process process = pb.start();
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException(
                "Maven dependency:tree timed out after " + TIMEOUT_SECONDS + " seconds");
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        if (process.exitValue() != 0) {
            throw new RuntimeException(
                "Maven exited with code " + process.exitValue() + ": " + output);
        }

        List<Dependency> deps = parseDependencyTree(output);
        log.info("Resolved {} dependencies for project at {}", deps.size(), projectDir);
        return new DependencyGraph(!deps.isEmpty(), false, deps,
            "Resolved " + deps.size() + " dependencies");
    }

    List<Dependency> parseDependencyTree(String output) {
        List<Dependency> deps = new ArrayList<>();
        String[] lines = output.split("\\R");

        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("---")) {
                continue;
            }
            String stripped = trimmed.replaceAll("^\\[INFO\\]\\s*", "");
            if (!stripped.startsWith("+-") && !stripped.startsWith("\\-") && !stripped.startsWith("|")) {
                continue;
            }
            String coord = stripped.replaceAll("^[\\s|+\\\\-]+", "").strip();

            Matcher matcher = DEP_COORD_PATTERN.matcher(coord);
            if (matcher.matches()) {
                String groupId = matcher.group(1);
                String artifactId = matcher.group(2);
                String version = matcher.group(4);
                String scope = matcher.group(5);
                deps.add(new Dependency(groupId, artifactId, version, scope));
            }
        }

        return deps;
    }

    boolean isMavenAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", "mvn");
            Process process = pb.start();
            boolean exited = process.waitFor(5, TimeUnit.SECONDS);
            return exited && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
