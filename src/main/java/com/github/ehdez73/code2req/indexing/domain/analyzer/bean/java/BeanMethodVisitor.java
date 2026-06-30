package com.github.ehdez73.code2req.indexing.domain.analyzer.bean.java;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AstAnalysisVisitor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class BeanMethodVisitor implements AstAnalysisVisitor {

    private static final Logger log = LoggerFactory.getLogger(BeanMethodVisitor.class);

    private static final Set<String> CONFIG_ANNOTATIONS = Set.of(
        "Configuration", "SpringBootApplication"
    );

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context) {
        List<BeanMethodInfo> beans = new ArrayList<>();
        cu.accept(new BeanMethodAstAdapter(context.filePath()), beans);
        if (!beans.isEmpty()) {
            log.info("  BeanMethodVisitor: found {} @Bean method(s) in {}", beans.size(), context.filePath());
            beans.forEach(builder::addFinding);
        }
    }

    static class BeanMethodAstAdapter extends VoidVisitorAdapter<List<BeanMethodInfo>> {

        private final String filePath;

        BeanMethodAstAdapter(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, List<BeanMethodInfo> collector) {
            if (n.isInterface() || n.isAbstract()) {
                super.visit(n, collector);
                return;
            }

            boolean isConfigClass = n.getAnnotations().stream()
                .anyMatch(ann -> CONFIG_ANNOTATIONS.contains(ann.getNameAsString()));

            if (!isConfigClass) {
                super.visit(n, collector);
                return;
            }

            String configClassName = n.getNameAsString();

            for (MethodDeclaration method : n.getMethods()) {
                for (AnnotationExpr ann : method.getAnnotations()) {
                    if ("Bean".equals(ann.getNameAsString())) {
                        String beanName = extractBeanName(ann).orElse(method.getNameAsString());
                        String returnType = method.getType().asString();
                        collector.add(new BeanMethodInfo(beanName, returnType, configClassName, filePath));
                    }
                }
            }
        }

        static Optional<String> extractBeanName(AnnotationExpr ann) {
            if (ann instanceof SingleMemberAnnotationExpr smae) {
                return Optional.of(smae.getMemberValue().toString().replaceAll("^\"|\"$", ""));
            }
            if (ann instanceof NormalAnnotationExpr nae) {
                for (MemberValuePair pair : nae.getPairs()) {
                    if ("name".equals(pair.getNameAsString()) || "value".equals(pair.getNameAsString())) {
                        Expression value = pair.getValue();
                        if (value instanceof StringLiteralExpr sle) {
                            return Optional.of(sle.getValue());
                        }
                        if (value instanceof ArrayInitializerExpr aie) {
                            List<Expression> values = aie.getValues();
                            if (!values.isEmpty() && values.get(0) instanceof StringLiteralExpr sle) {
                                return Optional.of(sle.getValue());
                            }
                        }
                        return Optional.of(value.toString().replaceAll("^\"|\"$", ""));
                    }
                }
            }
            return Optional.empty();
        }
    }
}
