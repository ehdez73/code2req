package com.github.ehdez73.code2req.analyzer.scheduledtask;

import com.github.ehdez73.code2req.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.analyzer.AstAnalysisVisitor;
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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ScheduledTaskVisitor implements AstAnalysisVisitor {

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, String filePath) {
        List<ScheduledTaskInfo> tasks = new ArrayList<>();
        cu.accept(new ScheduledTaskAstAdapter(filePath), tasks);
        tasks.forEach(builder::addFinding);
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
