package com.github.ehdez73.code2req.analyzer;

import com.github.ehdez73.code2req.analyzer.declaration.GlobalDeclarationRegistry;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class JavaAstAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(JavaAstAnalyzer.class);

    private final List<AstAnalysisVisitor> visitors;

    public JavaAstAnalyzer(List<AstAnalysisVisitor> visitors) {
        this.visitors = visitors;
    }

    public AnalysisResult analyze(Path filePath) {
        AnalysisContext context = new AnalysisContext(filePath.toString());
        return analyze(filePath, context);
    }

    public AnalysisResult analyze(Path filePath, AnalysisContext context) {
        String fp = context.filePath();
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            return analyze(cu, context);
        } catch (Exception e) {
            log.warn("Failed to analyze {}: {}", fp, e.getMessage());
            return new AnalysisResult(fp, List.of());
        }
    }

    public AnalysisResult analyze(String filePath, String content) {
        AnalysisContext context = new AnalysisContext(filePath);
        return analyze(filePath, content, context);
    }

    public AnalysisResult analyze(String filePath, String content, AnalysisContext context) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(content);
            return analyze(cu, context);
        } catch (Exception e) {
            log.warn("Failed to analyze {}: {}", filePath, e.getMessage());
            return new AnalysisResult(filePath, List.of());
        }
    }

    public AnalysisResult analyze(CompilationUnit cu, AnalysisContext context) {
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        for (AstAnalysisVisitor visitor : visitors) {
            visitor.analyze(cu, builder, context);
        }
        return builder.build(context.filePath());
    }
}
