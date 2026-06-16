package com.github.ehdez73.code2req.analyzer.template;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ThymeleafTemplateParser implements TemplateParser {

    private static final Pattern FORM = Pattern.compile(
        "<form\\s+[^>]*th:action=\"@\\{([^}]+)\\}\"[^>]*th:method=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORM_REVERSED = Pattern.compile(
        "<form\\s+[^>]*th:method=\"([^\"]*)\"[^>]*th:action=\"@\\{([^}]+)\\}\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORM_NO_METHOD = Pattern.compile(
        "<form\\s+[^>]*th:action=\"@\\{([^}]+)\\}\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK = Pattern.compile(
        "<a\\s+[^>]*th:href=\"@\\{([^}]+)\\}\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT_FIELD = Pattern.compile(
        "<input\\s+[^>]*th:field=\"\\*\\{([^}]+)\\}\"", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supports(Path filePath) {
        return filePath.toString().toLowerCase().endsWith(".html");
    }

    @Override
    public List<TemplateFormInfo> parse(String content, String filePath) {
        List<TemplateFormInfo> findings = new ArrayList<>();

        Matcher formMatcher = FORM.matcher(content);
        while (formMatcher.find()) {
            String action = formMatcher.group(1);
            String method = formMatcher.group(2).toUpperCase();
            boolean isExpr = containsExpression(action);
            List<String> fields = TemplateParser.extractFields(content, INPUT_FIELD, formMatcher.start(), formMatcher.end());
            findings.add(new TemplateFormInfo(method, action, isExpr, fields, "FORM", filePath));
        }

        Matcher formRevMatcher = FORM_REVERSED.matcher(content);
        while (formRevMatcher.find()) {
            if (TemplateParser.isOverlapping(findings, filePath)) continue;
            String method = formRevMatcher.group(1).toUpperCase();
            String action = formRevMatcher.group(2);
            boolean isExpr = containsExpression(action);
            List<String> fields = TemplateParser.extractFields(content, INPUT_FIELD, formRevMatcher.start(), formRevMatcher.end());
            findings.add(new TemplateFormInfo(method, action, isExpr, fields, "FORM", filePath));
        }

        Matcher formNoMethodMatcher = FORM_NO_METHOD.matcher(content);
        while (formNoMethodMatcher.find()) {
            if (TemplateParser.isOverlapping(findings, filePath)) continue;
            String action = formNoMethodMatcher.group(1);
            boolean isExpr = containsExpression(action);
            List<String> fields = TemplateParser.extractFields(content, INPUT_FIELD, formNoMethodMatcher.start(), formNoMethodMatcher.end());
            findings.add(new TemplateFormInfo("GET", action, isExpr, fields, "FORM", filePath));
        }

        Matcher linkMatcher = LINK.matcher(content);
        while (linkMatcher.find()) {
            String href = linkMatcher.group(1);
            if (!href.startsWith("http") && !href.startsWith("#")) {
                boolean isExpr = containsExpression(href);
                findings.add(new TemplateFormInfo("GET", href, isExpr, List.of(), "LINK", filePath));
            }
        }

        return findings;
    }

    private static boolean containsExpression(String url) {
        return url.contains("${") || url.contains("*{");
    }
}