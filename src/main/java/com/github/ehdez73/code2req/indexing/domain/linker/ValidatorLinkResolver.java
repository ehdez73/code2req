package com.github.ehdez73.code2req.indexing.domain.linker;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.validator.ValidatorInfo;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ValidatorLinkResolver {

    private static final Logger log = LoggerFactory.getLogger(ValidatorLinkResolver.class);

    private static final Set<String> BUILT_IN_ANNOTATIONS = Set.of(
        "NotNull", "NotEmpty", "NotBlank", "Size", "Min", "Max",
        "Email", "Pattern", "Positive", "PositiveOrZero", "Negative",
        "NegativeOrZero", "Past", "PastOrPresent", "Future", "FutureOrPresent",
        "AssertTrue", "AssertFalse", "DecimalMin", "DecimalMax", "Digits",
        "Valid", "Validated"
    );

    private final ExecutionFindingStore executionFindingStore;

    public ValidatorLinkResolver(ExecutionFindingStore executionFindingStore) {
        this.executionFindingStore = executionFindingStore;
    }

    public List<ValidatorLinkInfo> resolve(List<Path> allFiles) {
        Map<String, ValidatorInfo> validatorClasses = loadValidatorClasses();
        if (validatorClasses.isEmpty()) {
            log.info("ValidatorLinkResolver: no CONSTRAINT_VALIDATOR findings, skipping");
            return List.of();
        }
        log.info("ValidatorLinkResolver: {} validator class(es) found", validatorClasses.size());

        Map<String, String> constraintAnnotationMap = buildConstraintAnnotationMap(allFiles, validatorClasses);
        log.info("ValidatorLinkResolver: {} constraint annotation(s) mapped", constraintAnnotationMap.size());

        if (constraintAnnotationMap.isEmpty()) {
            return List.of();
        }

        List<ValidatorLinkInfo> links = resolveAnnotationUsages(allFiles, constraintAnnotationMap, validatorClasses);
        log.info("ValidatorLinkResolver: {} VALIDATOR_LINK(s) produced", links.size());
        return links;
    }

    private Map<String, ValidatorInfo> loadValidatorClasses() {
        List<Map<String, Object>> rows = executionFindingStore.findAllByType(FindingType.CONSTRAINT_VALIDATOR);
        Map<String, ValidatorInfo> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String json = (String) row.get("finding_json");
            if (json == null) continue;
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                ValidatorInfo vi = mapper.readValue(json, ValidatorInfo.class);
                result.put(vi.className(), vi);
            } catch (Exception e) {
                log.warn("Failed to deserialize CONSTRAINT_VALIDATOR: {}", e.getMessage());
            }
        }
        return result;
    }

    private Map<String, String> buildConstraintAnnotationMap(List<Path> allFiles,
                                                               Map<String, ValidatorInfo> validatorClasses) {
        Map<String, String> annotationToValidator = new HashMap<>();
        for (Path file : allFiles) {
            String path = file.toAbsolutePath().normalize().toString();
            if (!path.endsWith(".java")) continue;
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                CompilationUnit cu = StaticJavaParser.parse(content);
                cu.accept(new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(ClassOrInterfaceDeclaration n, Void v) {
                        if (!n.isAnnotationDeclaration()) return;
                        String annotationName = n.getNameAsString();
                        for (AnnotationExpr ann : n.getAnnotations()) {
                            if (!"Constraint".equals(ann.getNameAsString())) continue;
                            String validatedBy = extractValidatedBy(ann);
                            if (validatedBy == null) continue;
                            Set<String> matched = matchValidatedBy(validatedBy, validatorClasses);
                            for (String match : matched) {
                                annotationToValidator.put(annotationName, match);
                                log.debug("  ValidatorLinkResolver: @{} → {}", annotationName, match);
                            }
                        }
                    }
                }, null);
            } catch (Exception e) {
                log.debug("Failed to parse {} for constraint annotation: {}", path, e.getMessage());
            }
        }
        return annotationToValidator;
    }

    private static String extractValidatedBy(AnnotationExpr annotation) {
        try {
            if (annotation.isSingleMemberAnnotationExpr()) {
                return annotation.asSingleMemberAnnotationExpr().getMemberValue().toString();
            }
            if (annotation.isNormalAnnotationExpr()) {
                for (var pair : annotation.asNormalAnnotationExpr().getPairs()) {
                    if ("validatedBy".equals(pair.getNameAsString())) {
                        return pair.getValue().toString();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Set<String> matchValidatedBy(String validatedByExpr,
                                                 Map<String, ValidatorInfo> validatorClasses) {
        Set<String> matches = new HashSet<>();
        String clean = validatedByExpr
            .replace("{", "").replace("}", "")
            .replace(" ", "").replace(".class", "");
        String[] parts = clean.split(",");
        for (String part : parts) {
            String simpleName = part.contains(".")
                ? part.substring(part.lastIndexOf('.') + 1)
                : part;
            if (validatorClasses.containsKey(simpleName)) {
                matches.add(simpleName);
            }
        }
        return matches;
    }

    private List<ValidatorLinkInfo> resolveAnnotationUsages(List<Path> allFiles,
                                                              Map<String, String> constraintAnnotationMap,
                                                              Map<String, ValidatorInfo> validatorClasses) {
        List<ValidatorLinkInfo> links = new ArrayList<>();

        for (Path file : allFiles) {
            String path = file.toAbsolutePath().normalize().toString();
            if (!path.endsWith(".java")) continue;
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                CompilationUnit cu = StaticJavaParser.parse(content);
                List<AnnotationUsage> usages = collectAnnotationUsages(cu);
                for (AnnotationUsage usage : usages) {
                    String annName = usage.annotationName();
                    String validatorClassName = constraintAnnotationMap.get(annName);
                    if (validatorClassName == null) continue;

                    ValidatorInfo vi = validatorClasses.get(validatorClassName);
                    links.add(new ValidatorLinkInfo(
                        path,
                        usage.className(),
                        usage.elementName(),
                        annName,
                        vi.filePath(),
                        vi.className(),
                        vi.isValidBody()
                    ));
                }
            } catch (Exception e) {
                log.debug("Failed to parse {} for annotation usages: {}", path, e.getMessage());
            }
        }
        return links;
    }

    private static List<AnnotationUsage> collectAnnotationUsages(CompilationUnit cu) {
        List<AnnotationUsage> usages = new ArrayList<>();
        cu.accept(new VoidVisitorAdapter<Void>() {
            private String currentClass = "";

            @Override
            public void visit(ClassOrInterfaceDeclaration n, Void v) {
                String prev = currentClass;
                currentClass = n.getNameAsString();
                super.visit(n, v);
                currentClass = prev;
            }

            @Override
            public void visit(RecordDeclaration n, Void v) {
                String prev = currentClass;
                currentClass = n.getNameAsString();
                for (Parameter param : n.getParameters()) {
                    for (AnnotationExpr ann : param.getAnnotations()) {
                        String name = ann.getNameAsString();
                        if (!BUILT_IN_ANNOTATIONS.contains(name)) {
                            usages.add(new AnnotationUsage(currentClass, param.getNameAsString(), name));
                        }
                    }
                }
                super.visit(n, v);
                currentClass = prev;
            }

            @Override
            public void visit(FieldDeclaration n, Void v) {
                for (AnnotationExpr ann : n.getAnnotations()) {
                    String name = ann.getNameAsString();
                    if (!BUILT_IN_ANNOTATIONS.contains(name)) {
                        String fieldName = n.getVariables().isEmpty()
                            ? "" : n.getVariables().get(0).getNameAsString();
                        usages.add(new AnnotationUsage(currentClass, fieldName, name));
                    }
                }
            }

            @Override
            public void visit(MethodDeclaration n, Void v) {
                for (Parameter param : n.getParameters()) {
                    for (AnnotationExpr ann : param.getAnnotations()) {
                        String name = ann.getNameAsString();
                        if (!BUILT_IN_ANNOTATIONS.contains(name)) {
                            usages.add(new AnnotationUsage(currentClass, param.getNameAsString(), name));
                        }
                    }
                }
            }
        }, null);
        return usages;
    }

    private record AnnotationUsage(String className, String elementName, String annotationName) {}
}
