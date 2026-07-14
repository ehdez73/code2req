package com.github.ehdez73.code2req.indexing.domain.analyzer.declaration;

import com.github.ehdez73.code2req.indexing.domain.model.AllowedLibrariesConfig;
import com.github.ehdez73.code2req.indexing.domain.model.IndexingConfig;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TestImportIndex {

    private final Map<String, List<String>> importToTests = new ConcurrentHashMap<>();
    private final Map<String, String> fqnByPath = new ConcurrentHashMap<>();
    private final List<String> testSuffixes;
    private final List<String> testPrefixes;
    private final AllowedLibrariesConfig allowedLibrariesConfig;

    public TestImportIndex(IndexingConfig indexingConfig, AllowedLibrariesConfig allowedLibrariesConfig) {
        this.testSuffixes = indexingConfig.resolvedTestSuffixes();
        this.testPrefixes = indexingConfig.resolvedTestPrefixes();
        this.allowedLibrariesConfig = allowedLibrariesConfig;
    }

    public void index(String filePath, CompilationUnit cu) {
        String ownFqn = resolveOwnFqn(cu);
        if (ownFqn == null) return;

        fqnByPath.put(filePath, ownFqn);

        if (!isTestFile(filePath, ownFqn)) return;

        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isAsterisk() || imp.isStatic()) continue;
            String importedFqn = imp.getNameAsString();
            if (isFrameworkOrJdk(importedFqn)) continue;
            importToTests.computeIfAbsent(importedFqn, k -> new ArrayList<>()).add(filePath);
        }

        inferProductionClass(filePath, ownFqn);
    }

    public Optional<String> findTestFileBySourcePath(String sourceFilePath) {
        String ownFqn = fqnByPath.get(sourceFilePath);
        if (ownFqn == null) return Optional.empty();

        if (isSourceFileATestFile(sourceFilePath, ownFqn)) return Optional.empty();

        var tests = importToTests.get(ownFqn);
        if (tests == null || tests.isEmpty()) return Optional.empty();
        return tests.stream().sorted().findFirst();
    }

    public boolean hasTests(String sourceFilePath) {
        return findTestFileBySourcePath(sourceFilePath).isPresent();
    }

    public void clear() {
        importToTests.clear();
        fqnByPath.clear();
    }

    private static String resolveOwnFqn(CompilationUnit cu) {
        String packageName = cu.getPackageDeclaration()
            .map(pd -> pd.getNameAsString())
            .orElse("");
        var types = cu.getTypes();
        if (types.isEmpty()) return null;
        String className = types.get(0).getNameAsString();
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    private boolean isTestFile(String filePath, String ownFqn) {
        if (ownFqn == null) return false;
        if (filePath.contains("/src/main/")) return false;

        String className = ownFqn.substring(ownFqn.lastIndexOf('.') + 1);
        if (testSuffixes.stream().anyMatch(className::endsWith)) return true;
        return testPrefixes.stream().anyMatch(className::startsWith);
    }

    private void inferProductionClass(String filePath, String ownFqn) {
        String className = ownFqn.substring(ownFqn.lastIndexOf('.') + 1);
        String packageName = ownFqn.contains(".")
            ? ownFqn.substring(0, ownFqn.lastIndexOf('.'))
            : "";

        for (String suffix : testSuffixes) {
            if (className.endsWith(suffix) && className.length() > suffix.length()) {
                String stripped = className.substring(0, className.length() - suffix.length());
                String inferredFqn = packageName.isEmpty() ? stripped : packageName + "." + stripped;
                importToTests.computeIfAbsent(inferredFqn, k -> new ArrayList<>()).add(filePath);
            }
        }

        for (String prefix : testPrefixes) {
            if (className.startsWith(prefix) && className.length() > prefix.length()) {
                String stripped = className.substring(prefix.length());
                String inferredFqn = packageName.isEmpty() ? stripped : packageName + "." + stripped;
                importToTests.computeIfAbsent(inferredFqn, k -> new ArrayList<>()).add(filePath);
            }
        }
    }

    private boolean isSourceFileATestFile(String filePath, String ownFqn) {
        if (filePath.contains("/src/test/")) return true;
        if (filePath.contains("/src/integration-test/")) return true;

        String className = ownFqn.substring(ownFqn.lastIndexOf('.') + 1);
        if (testSuffixes.stream().anyMatch(className::endsWith)) return true;
        return testPrefixes.stream().anyMatch(className::startsWith);
    }

    private boolean isFrameworkOrJdk(String fqn) {
        for (String prefix : allowedLibrariesConfig.resolvedJdkPrefixes()) {
            if (fqn.startsWith(prefix)) return true;
        }
        for (String prefix : allowedLibrariesConfig.resolvedFrameworkPrefixes()) {
            if (fqn.startsWith(prefix)) return true;
        }
        return false;
    }
}
