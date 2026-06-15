package com.github.ehdez73.code2req.analyzer.activemq;

import com.github.ehdez73.code2req.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.analyzer.AstAnalysisVisitor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ActiveMqVisitor implements AstAnalysisVisitor {

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context) {
        ActiveMqCollector collector = new ActiveMqCollector();
        cu.accept(new ActiveMqAstAdapter(context.filePath()), collector);
        collector.listeners.forEach(builder::addFinding);
        collector.publishers.forEach(builder::addFinding);
    }

    static class ActiveMqCollector {
        final List<ActiveMqInfo> listeners = new ArrayList<>();
        final List<ActiveMqPublisherInfo> publishers = new ArrayList<>();
    }

    static class ActiveMqAstAdapter extends VoidVisitorAdapter<ActiveMqCollector> {

        private final String filePath;
        private String className = "";

        ActiveMqAstAdapter(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, ActiveMqCollector collector) {
            className = n.getNameAsString();
            super.visit(n, collector);
        }

        @Override
        public void visit(MethodDeclaration n, ActiveMqCollector collector) {
            for (AnnotationExpr ann : n.getAnnotations()) {
                if ("JmsListener".equals(ann.getNameAsString())) {
                    String destination = extractDestination(ann);
                    collector.listeners.add(new ActiveMqInfo(
                        destination,
                        n.getNameAsString(),
                        className,
                        filePath
                    ));
                }
            }

            n.getBody().ifPresent(body ->
                body.findAll(MethodCallExpr.class).forEach(mce -> {
                    if ("convertAndSend".equals(mce.getNameAsString())) {
                        String scope = mce.getScope().map(Object::toString).orElse("");
                        if (scope.toLowerCase().contains("jmstemplate")) {
                            String destination = mce.getArguments().isEmpty()
                                ? "" : extractStringLiteral(mce.getArgument(0));
                            collector.publishers.add(
                                new ActiveMqPublisherInfo(
                                    destination, n.getNameAsString(), className, filePath
                                )
                            );
                        }
                    }
                })
            );
        }

        static String extractDestination(AnnotationExpr ann) {
            if (ann instanceof NormalAnnotationExpr nae) {
                for (MemberValuePair pair : nae.getPairs()) {
                    if ("destination".equals(pair.getNameAsString())) {
                        return extractStringLiteral(pair.getValue());
                    }
                }
            }
            return "";
        }

        static String extractStringLiteral(Expression arg) {
            if (arg.isStringLiteralExpr()) {
                return arg.asStringLiteralExpr().getValue();
            }
            if (arg instanceof NameExpr) {
                return arg.toString();
            }
            if (arg instanceof FieldAccessExpr) {
                return arg.toString();
            }
            return arg.toString();
        }
    }
}
