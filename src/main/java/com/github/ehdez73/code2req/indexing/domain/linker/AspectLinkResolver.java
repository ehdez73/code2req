package com.github.ehdez73.code2req.indexing.domain.linker;

import com.github.ehdez73.code2req.indexing.domain.analyzer.aspect.AspectInfo;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AspectLinkResolver {

    private static final Logger log = LoggerFactory.getLogger(AspectLinkResolver.class);

    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("@(?:annotation|within)\\(\\s*([a-zA-Z_][\\w.]*)\\s*\\)");

    private final ExecutionFindingStore executionFindingStore;

    public AspectLinkResolver(ExecutionFindingStore executionFindingStore) {
        this.executionFindingStore = executionFindingStore;
    }

    public List<AopAdviceLinkInfo> resolve(List<Path> allFiles) {
        List<AspectAdviceEntry> adviceEntries = loadAspectAdvice();
        if (adviceEntries.isEmpty()) {
            log.info("AspectLinkResolver: no ASPECT_ADVICE findings, skipping");
            return List.of();
        }
        log.info("AspectLinkResolver: {} advice method(s) loaded", adviceEntries.size());

        List<AspectAdviceEntry> parsedEntries = new ArrayList<>();
        for (var entry : adviceEntries) {
            AnnotationPattern ap = parsePointcut(entry.pointcutExpression());
            if (ap != null) {
                parsedEntries.add(entry.withAnnotationPattern(ap));
            } else {
                log.debug("AspectLinkResolver: skipping complex pointcut: {}", entry.pointcutExpression());
            }
        }

        if (parsedEntries.isEmpty()) {
            log.info("AspectLinkResolver: no parseable pointcuts, skipping");
            return List.of();
        }

        List<AopAdviceLinkInfo> links = matchAnnotationsToMethods(allFiles, parsedEntries);
        log.info("AspectLinkResolver: {} AOP_ADVICE_LINK(s) produced", links.size());
        return links;
    }

    private List<AspectAdviceEntry> loadAspectAdvice() {
        List<Map<String, Object>> rows = executionFindingStore.findAllByType(FindingType.ASPECT_ADVICE);
        List<AspectAdviceEntry> entries = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String json = (String) row.get("finding_json");
            if (json == null) continue;
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                AspectInfo ai = mapper.readValue(json, AspectInfo.class);
                entries.add(new AspectAdviceEntry(
                    ai.filePath(), ai.className(), ai.methodName(),
                    ai.adviceType(), ai.pointcutExpression()
                ));
            } catch (Exception e) {
                log.warn("Failed to deserialize ASPECT_ADVICE: {}", e.getMessage());
            }
        }
        return entries;
    }

    private static AnnotationPattern parsePointcut(String pointcut) {
        if (pointcut == null || pointcut.isBlank()) return null;
        String clean = pointcut.replace("\"", "").trim();

        Matcher matcher = ANNOTATION_PATTERN.matcher(clean);
        if (!matcher.find()) {
            return null;
        }
        String matchType = matcher.group(0).startsWith("@annotation") ? "annotation" : "within";
        String annotationFqn = matcher.group(1);
        return new AnnotationPattern(matchType, annotationFqn);
    }

    private List<AopAdviceLinkInfo> matchAnnotationsToMethods(List<Path> allFiles,
                                                                List<AspectAdviceEntry> entries) {
        List<AopAdviceLinkInfo> links = new ArrayList<>();

        Map<String, List<AspectAdviceEntry>> classLevelEntries = new HashMap<>();
        Map<String, List<AspectAdviceEntry>> methodLevelEntries = new HashMap<>();

        for (var entry : entries) {
            if ("within".equals(entry.annotationPattern.matchType())) {
                classLevelEntries.computeIfAbsent(entry.annotationPattern.annotationFqn(), k -> new ArrayList<>()).add(entry);
            } else {
                methodLevelEntries.computeIfAbsent(entry.annotationPattern.annotationFqn(), k -> new ArrayList<>()).add(entry);
            }
        }

        for (Path file : allFiles) {
            String path = file.toAbsolutePath().normalize().toString();
            if (!path.endsWith(".java")) continue;
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                CompilationUnit cu = StaticJavaParser.parse(content);
                cu.accept(new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(ClassOrInterfaceDeclaration n, Void v) {
                        String className = n.getNameAsString();

                        for (AnnotationExpr ann : n.getAnnotations()) {
                            String annName = ann.getNameAsString();
                            List<AspectAdviceEntry> classMatches = classLevelEntries.get(annName);
                            if (classMatches != null) {
                                for (var entry : classMatches) {
                                    for (MethodDeclaration method : n.getMethods()) {
                                        links.add(new AopAdviceLinkInfo(
                                            path, className, method.getNameAsString(),
                                            annName,
                                            entry.sourceFile, entry.aspectClass, entry.adviceMethod,
                                            entry.adviceType, entry.pointcutExpression
                                        ));
                                    }
                                }
                            }
                        }

                        for (MethodDeclaration method : n.getMethods()) {
                            for (AnnotationExpr ann : method.getAnnotations()) {
                                String annName = ann.getNameAsString();
                                List<AspectAdviceEntry> methodMatches = methodLevelEntries.get(annName);
                                if (methodMatches != null) {
                                    for (var entry : methodMatches) {
                                        links.add(new AopAdviceLinkInfo(
                                            path, className, method.getNameAsString(),
                                            annName,
                                            entry.sourceFile, entry.aspectClass, entry.adviceMethod,
                                            entry.adviceType, entry.pointcutExpression
                                        ));
                                    }
                                }
                            }
                        }
                    }
                }, null);
            } catch (Exception e) {
                log.debug("Failed to parse {} for annotation matching: {}", path, e.getMessage());
            }
        }

        return links;
    }

    private record AspectAdviceEntry(String sourceFile, String aspectClass, String adviceMethod,
                                      String adviceType, String pointcutExpression,
                                      AnnotationPattern annotationPattern) {
        AspectAdviceEntry(String sourceFile, String aspectClass, String adviceMethod,
                          String adviceType, String pointcutExpression) {
            this(sourceFile, aspectClass, adviceMethod, adviceType, pointcutExpression, null);
        }

        AspectAdviceEntry withAnnotationPattern(AnnotationPattern p) {
            return new AspectAdviceEntry(sourceFile, aspectClass, adviceMethod, adviceType, pointcutExpression, p);
        }
    }

    private record AnnotationPattern(String matchType, String annotationFqn) {}
}
