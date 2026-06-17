package com.github.ehdez73.code2req.analyzer.event.broker.kafka;

import com.github.ehdez73.code2req.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.analyzer.AstAnalysisVisitor;
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
public class KafkaVisitor implements AstAnalysisVisitor {

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context) {
        KafkaCollector collector = new KafkaCollector();
        cu.accept(new KafkaAstAdapter(context.filePath()), collector);
        collector.listeners.forEach(builder::addFinding);
        collector.publishers.forEach(builder::addFinding);
    }

    static class KafkaCollector {
        final List<KafkaInfo> listeners = new ArrayList<>();
        final List<KafkaPublisherInfo> publishers = new ArrayList<>();
    }

    static class KafkaAstAdapter extends VoidVisitorAdapter<KafkaCollector> {

        private final String filePath;
        private String className = "";

        KafkaAstAdapter(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, KafkaCollector collector) {
            className = n.getNameAsString();
            super.visit(n, collector);
        }

        @Override
        public void visit(MethodDeclaration n, KafkaCollector collector) {
            for (AnnotationExpr ann : n.getAnnotations()) {
                if ("KafkaListener".equals(ann.getNameAsString())) {
                    ParsedKafkaTopics parsed = extractTopics(ann);
                    collector.listeners.add(new KafkaInfo(
                        parsed.topics(),
                        n.getNameAsString(),
                        className,
                        filePath,
                        parsed.isPattern()
                    ));
                }
            }

            n.getBody().ifPresent(body ->
                body.findAll(MethodCallExpr.class).forEach(mce -> {
                    if ("send".equals(mce.getNameAsString())) {
                        String scope = mce.getScope().map(Object::toString).orElse("");
                        if (scope.toLowerCase().contains("kafkatemplate")) {
                            String topic = mce.getArguments().isEmpty()
                                ? "" : extractStringLiteral(mce.getArgument(0));
                            collector.publishers.add(
                                new KafkaPublisherInfo(topic, n.getNameAsString(), className, filePath)
                            );
                        }
                    }
                })
            );
        }

        static ParsedKafkaTopics extractTopics(AnnotationExpr ann) {
            if (ann instanceof NormalAnnotationExpr nae) {
                for (MemberValuePair pair : nae.getPairs()) {
                    String name = pair.getNameAsString();
                    if ("topics".equals(name)) {
                        return new ParsedKafkaTopics(extractTopicValue(pair.getValue()), false);
                    }
                    if ("topicPattern".equals(name)) {
                        return new ParsedKafkaTopics(
                            pair.getValue().toString().replaceAll("^\"|\"$", ""), true);
                    }
                    if ("topicPartitions".equals(name)) {
                        return new ParsedKafkaTopics(
                            "[topicPartitions]", false);
                    }
                }
            }
            return new ParsedKafkaTopics("", false);
        }

        static String extractTopicValue(Expression value) {
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

        record ParsedKafkaTopics(String topics, boolean isPattern) {}
    }
}
