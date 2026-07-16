package com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AstAnalysisVisitor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class ImportResourceVisitor implements AstAnalysisVisitor {

    private static final Logger log = LoggerFactory.getLogger(ImportResourceVisitor.class);

    private final XmlBeanAnalyzer xmlBeanAnalyzer;

    public ImportResourceVisitor(XmlBeanAnalyzer xmlBeanAnalyzer) {
        this.xmlBeanAnalyzer = xmlBeanAnalyzer;
    }

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context) {
        List<String> resources = new ArrayList<>();
        cu.accept(new ImportResourceAdapter(context.filePath(), context.sourceRoot()), resources);
        if (resources.isEmpty()) return;

        Path javaFileDir = Path.of(context.filePath()).getParent();
        Path sourceRoot = context.sourceRoot() != null && !context.sourceRoot().isEmpty()
            ? Path.of(context.sourceRoot())
            : null;

        for (String resource : resources) {
            List<Path> resolvedPaths = ResourcePathResolver.resolve(resource, sourceRoot, javaFileDir);
            if (resolvedPaths.isEmpty()) {
                log.warn("Could not resolve @ImportResource path '{}' from {}", resource, context.filePath());
                continue;
            }
            for (Path resolved : resolvedPaths) {
                List<AnalysisFinding> findings = xmlBeanAnalyzer.analyze(resolved, sourceRoot);
                for (AnalysisFinding finding : findings) {
                    builder.addFinding(finding);
                }
                if (!findings.isEmpty()) {
                    log.info("  ImportResourceVisitor: resolved '{}' → {} ({} finding(s))",
                        resource, resolved, findings.size());
                }
            }
        }
    }

    private static class ImportResourceAdapter extends VoidVisitorAdapter<List<String>> {

        private final String filePath;
        private final String sourceRoot;

        ImportResourceAdapter(String filePath, String sourceRoot) {
            this.filePath = filePath;
            this.sourceRoot = sourceRoot;
        }

        @Override
        public void visit(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration n, List<String> collector) {
            for (AnnotationExpr ann : n.getAnnotations()) {
                if (!"ImportResource".equals(ann.getNameAsString())) {
                    continue;
                }
                extractResourcePaths(ann, collector);
            }
            super.visit(n, collector);
        }

        private void extractResourcePaths(AnnotationExpr ann, List<String> collector) {
            if (ann instanceof SingleMemberAnnotationExpr smae) {
                addValues(smae.getMemberValue(), collector);
                return;
            }
            if (ann instanceof NormalAnnotationExpr nae) {
                boolean hasLocations = false;
                boolean hasValue = false;
                for (MemberValuePair pair : nae.getPairs()) {
                    String name = pair.getNameAsString();
                    if ("locations".equals(name)) {
                        addValues(pair.getValue(), collector);
                        hasLocations = true;
                    } else if ("value".equals(name)) {
                        addValues(pair.getValue(), collector);
                        hasValue = true;
                    }
                }
                if (!hasLocations && !hasValue) {
                    log.warn("Unrecognized @ImportResource annotation structure in {}", filePath);
                }
            }
        }

        private void addValues(Expression expr, List<String> collector) {
            if (expr instanceof StringLiteralExpr sle) {
                collector.add(sle.getValue());
            } else if (expr instanceof ArrayInitializerExpr aie) {
                for (Expression element : aie.getValues()) {
                    if (element instanceof StringLiteralExpr sle) {
                        collector.add(sle.getValue());
                    }
                }
            }
        }
    }
}
