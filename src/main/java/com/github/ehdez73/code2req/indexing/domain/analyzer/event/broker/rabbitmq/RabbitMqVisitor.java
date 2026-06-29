package com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.rabbitmq;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AstAnalysisVisitor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
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
import java.util.stream.Collectors;

@Component
public class RabbitMqVisitor implements AstAnalysisVisitor {

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context) {
        RabbitMqCollector collector = new RabbitMqCollector();
        cu.accept(new RabbitMqAstAdapter(context.filePath()), collector);
        collector.listeners.forEach(builder::addFinding);
        collector.publishers.forEach(builder::addFinding);
    }

    static class RabbitMqCollector {
        final List<RabbitMqInfo> listeners = new ArrayList<>();
        final List<RabbitMqPublisherInfo> publishers = new ArrayList<>();
    }

    static class RabbitMqAstAdapter extends VoidVisitorAdapter<RabbitMqCollector> {

        private final String filePath;
        private String className = "";

        RabbitMqAstAdapter(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, RabbitMqCollector collector) {
            className = n.getNameAsString();
            super.visit(n, collector);
        }

        @Override
        public void visit(MethodDeclaration n, RabbitMqCollector collector) {
            for (AnnotationExpr ann : n.getAnnotations()) {
                if ("RabbitListener".equals(ann.getNameAsString())) {
                    String queues = extractQueues(ann);
                    collector.listeners.add(new RabbitMqInfo(
                        queues,
                        n.getNameAsString(),
                        className,
                        filePath
                    ));
                }
            }

            n.getBody().ifPresent(body ->
                body.findAll(MethodCallExpr.class).forEach(mce -> {
                    String methodName = mce.getNameAsString();
                    if ("convertAndSend".equals(methodName) || "send".equals(methodName)) {
                        String scope = mce.getScope().map(Object::toString).orElse("");
                        if (scope.toLowerCase().contains("rabbittemplate")) {
                            List<Expression> args = mce.getArguments();
                            String exchange = args.size() > 0
                                ? extractStringLiteral(args.get(0)) : "";
                            String routingKey = args.size() > 1
                                ? extractStringLiteral(args.get(1)) : "";
                            collector.publishers.add(
                                new RabbitMqPublisherInfo(
                                    exchange, routingKey,
                                    n.getNameAsString(), className, filePath
                                )
                            );
                        }
                    }
                })
            );
        }

        static String extractQueues(AnnotationExpr ann) {
            if (ann instanceof NormalAnnotationExpr nae) {
                for (MemberValuePair pair : nae.getPairs()) {
                    if ("queues".equals(pair.getNameAsString())) {
                        return extractQueueValue(pair.getValue());
                    }
                }
            }
            return "";
        }

        static String extractQueueValue(Expression value) {
            if (value.isStringLiteralExpr()) {
                return value.asStringLiteralExpr().getValue();
            }
            if (value.isArrayInitializerExpr()) {
                return value.asArrayInitializerExpr().getValues().stream()
                    .map(v -> v.isStringLiteralExpr()
                        ? v.asStringLiteralExpr().getValue()
                        : stripQuotes(v.toString()))
                    .collect(Collectors.joining(", "));
            }
            return stripQuotes(value.toString());
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

        private static String stripQuotes(String s) {
            return s.replaceAll("^\"|\"$", "");
        }
    }
}
