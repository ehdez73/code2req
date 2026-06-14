package com.github.ehdez73.code2req.analyzer;

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
        String fp = filePath.toString();
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            AnalysisResultBuilder builder = new AnalysisResultBuilder();
            for (AstAnalysisVisitor visitor : visitors) {
                visitor.analyze(cu, builder, fp);
            }
            return builder.build(fp);
        } catch (Exception e) {
            log.warn("Failed to analyze {}: {}", fp, e.getMessage());
            return new AnalysisResult(fp, List.of(), List.of(), List.of());
        }
    }
}
