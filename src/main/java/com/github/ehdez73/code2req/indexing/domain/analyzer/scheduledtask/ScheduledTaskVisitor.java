package com.github.ehdez73.code2req.indexing.domain.analyzer.scheduledtask;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AstAnalysisVisitor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ScheduledTaskVisitor implements AstAnalysisVisitor {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskVisitor.class);

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context) {
        List<ScheduledTaskInfo> tasks = new ArrayList<>();
        cu.accept(new ScheduledTaskAstAdapter(context.filePath()), tasks);
        if (!tasks.isEmpty()) {
            log.info("  ScheduledTaskVisitor: found {} scheduled task(s) in {}", tasks.size(), context.filePath());
            tasks.forEach(builder::addFinding);
        }
    }

    static class ScheduledTaskAstAdapter extends VoidVisitorAdapter<List<ScheduledTaskInfo>> {

        private final String filePath;
        private String className = "";

        ScheduledTaskAstAdapter(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, List<ScheduledTaskInfo> collector) {
            className = n.getNameAsString();
            super.visit(n, collector);
        }

        @Override
        public void visit(MethodDeclaration n, List<ScheduledTaskInfo> collector) {
            for (AnnotationExpr ann : n.getAnnotations()) {
                if (!"Scheduled".equals(ann.getNameAsString())) {
                    continue;
                }

                Optional<String> cron = extractStringAttr(ann, "cron");
                Optional<Long> fixedRate = extractLongAttr(ann, "fixedRate");
                Optional<Long> fixedDelay = extractLongAttr(ann, "fixedDelay");

                String taskType;
                if (cron.isPresent()) {
                    taskType = "cron";
                } else if (fixedRate.isPresent()) {
                    taskType = "fixed-rate";
                } else if (fixedDelay.isPresent()) {
                    taskType = "fixed-delay";
                } else {
                    taskType = "unknown";
                }

                collector.add(new ScheduledTaskInfo(
                    n.getNameAsString(),
                    className,
                    cron.orElse(null),
                    fixedRate.orElse(null),
                    fixedDelay.orElse(null),
                    taskType,
                    filePath
                ));
            }
        }

        static Optional<String> extractStringAttr(AnnotationExpr ann, String attr) {
            if (ann instanceof NormalAnnotationExpr nae) {
                for (MemberValuePair pair : nae.getPairs()) {
                    if (pair.getNameAsString().equals(attr)) {
                        if (pair.getValue() instanceof StringLiteralExpr sle) {
                            return Optional.of(sle.getValue());
                        }
                        return Optional.of(pair.getValue().toString().replaceAll("^\"|\"$", ""));
                    }
                }
            }
            return Optional.empty();
        }

        static Optional<Long> extractLongAttr(AnnotationExpr ann, String attr) {
            if (ann instanceof NormalAnnotationExpr nae) {
                for (MemberValuePair pair : nae.getPairs()) {
                    if (pair.getNameAsString().equals(attr)) {
                        if (pair.getValue() instanceof IntegerLiteralExpr ile) {
                            return Optional.of((long) ile.asInt());
                        }
                        if (pair.getValue() instanceof LongLiteralExpr lle) {
                            return Optional.of(lle.asLong());
                        }
                        try {
                            return Optional.of(Long.parseLong(pair.getValue().toString()));
                        } catch (NumberFormatException e) {
                            return Optional.empty();
                        }
                    }
                }
            }
            return Optional.empty();
        }
    }
}
