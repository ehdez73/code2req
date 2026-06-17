package com.github.ehdez73.code2req.analyzer.event.listener;

import com.github.ehdez73.code2req.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.analyzer.AstAnalysisVisitor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EventListenerVisitor implements AstAnalysisVisitor {

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context) {
        EventListenerCollector collector = new EventListenerCollector();
        cu.accept(new EventListenerAstAdapter(context.filePath()), collector);
        collector.listeners.forEach(builder::addFinding);
        collector.publishers.forEach(builder::addFinding);
    }

    static class EventListenerCollector {
        final List<EventListenerInfo> listeners = new ArrayList<>();
        final List<EventPublisherInfo> publishers = new ArrayList<>();
    }

    static class EventListenerAstAdapter extends VoidVisitorAdapter<EventListenerCollector> {

        private final String filePath;
        private String className = "";

        EventListenerAstAdapter(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, EventListenerCollector collector) {
            className = n.getNameAsString();
            super.visit(n, collector);
        }

        @Override
        public void visit(MethodDeclaration n, EventListenerCollector collector) {
            for (AnnotationExpr ann : n.getAnnotations()) {
                if ("EventListener".equals(ann.getNameAsString())) {
                    String eventType = extractEventType(ann, n);
                    List<MethodCallInfo> callChain = extractCallChain(n);
                    collector.listeners.add(new EventListenerInfo(
                        eventType, n.getNameAsString(), className, filePath, callChain
                    ));
                }
            }

            n.getBody().ifPresent(body ->
                body.findAll(MethodCallExpr.class).forEach(mce -> {
                    if ("publishEvent".equals(mce.getNameAsString())) {
                        String eventType = mce.getArguments().isEmpty()
                            ? "" : extractEventTypeFromArg(mce.getArgument(0));
                        collector.publishers.add(
                            new EventPublisherInfo(className, eventType, filePath));
                    }
                })
            );
        }

        static String extractEventType(AnnotationExpr ann, MethodDeclaration method) {
            if (ann instanceof NormalAnnotationExpr nae) {
                for (MemberValuePair pair : nae.getPairs()) {
                    if ("value".equals(pair.getNameAsString())
                        || "classes".equals(pair.getNameAsString())) {
                        if (pair.getValue() instanceof ClassExpr ce) {
                            return ce.getTypeAsString();
                        }
                        return pair.getValue().toString().replaceAll("^\"|\"$", "");
                    }
                }
            }
            if (ann instanceof SingleMemberAnnotationExpr smae) {
                if (smae.getMemberValue() instanceof ClassExpr ce) {
                    return ce.getTypeAsString();
                }
                return smae.getMemberValue().toString().replaceAll("^\"|\"$", "");
            }
            if (!method.getParameters().isEmpty()) {
                return method.getParameter(0).getTypeAsString();
            }
            return "";
        }

        static List<MethodCallInfo> extractCallChain(MethodDeclaration method) {
            List<MethodCallInfo> calls = new ArrayList<>();
            method.getBody().ifPresent(body -> {
                for (MethodCallExpr mce : body.findAll(MethodCallExpr.class)) {
                    int depth = methodCallDepth(mce);
                    if (depth > 3) continue;
                    String scope = mce.getScope().map(Object::toString).orElse("");
                    calls.add(new MethodCallInfo(scope, mce.getNameAsString(), depth));
                }
            });
            return calls;
        }

        static int methodCallDepth(MethodCallExpr mce) {
            int depth = 1;
            Node parent = mce.getParentNode().orElse(null);
            while (parent != null) {
                if (parent instanceof MethodCallExpr) {
                    depth++;
                }
                if (parent instanceof MethodDeclaration) {
                    break;
                }
                parent = parent.getParentNode().orElse(null);
            }
            return depth;
        }

        static String extractEventTypeFromArg(Expression arg) {
            if (arg instanceof ObjectCreationExpr oce) {
                return oce.getTypeAsString();
            }
            return arg.toString();
        }
    }
}
