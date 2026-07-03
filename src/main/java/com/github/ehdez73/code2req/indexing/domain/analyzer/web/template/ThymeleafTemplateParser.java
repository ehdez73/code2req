package com.github.ehdez73.code2req.indexing.domain.analyzer.web.template;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ThymeleafTemplateParser implements TemplateParser {

    private static final Pattern FORM_TAG = Pattern.compile(
        "<form\\b([^>]*)>", Pattern.CASE_INSENSITIVE);

    private static final String URL_CONTENT = "((?:[^{}]|\\$\\{[^}]*\\}|\\*\\{[^}]*\\}|\\{[^}]*\\})+)";

    private static final Pattern TH_ACTION_ATTR = Pattern.compile(
        "th:action=\"@\\{" + URL_CONTENT + "\\}", Pattern.CASE_INSENSITIVE);

    private static final Pattern PLAIN_ACTION_ATTR = Pattern.compile(
        "action=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private static final Pattern TH_METHOD_ATTR = Pattern.compile(
        "th:method=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private static final Pattern PLAIN_METHOD_ATTR = Pattern.compile(
        "method=\"(get|post|put|delete|patch)\"", Pattern.CASE_INSENSITIVE);

    private static final Pattern LINK = Pattern.compile(
        "<a\\s+[^>]*th:href=\"@\\{" + URL_CONTENT + "\\}", Pattern.CASE_INSENSITIVE);

    private static final Pattern INPUT_FIELD = Pattern.compile(
        "<input\\s+[^>]*th:field=\"\\*\\{" + URL_CONTENT + "\\}", Pattern.CASE_INSENSITIVE);

    private static final Pattern INPUT_VALUE_SELECT = Pattern.compile(
        "<input\\s+[^>]*th:value=\"\\*\\{" + URL_CONTENT + "\\}", Pattern.CASE_INSENSITIVE);

    private static final Pattern INPUT_VALUE_VAR = Pattern.compile(
        "<input\\s+[^>]*th:value=\"\\$\\{([^}]+)\\}\"", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supports(Path filePath) {
        return filePath.toString().toLowerCase().endsWith(".html");
    }

    @Override
    public List<TemplateFormInfo> parse(String content, String filePath) {
        List<TemplateFormInfo> findings = new ArrayList<>();
        String normalizedPath = filePath != null ? filePath.replace('\\', '/') : "";
        boolean isFragment = normalizedPath.contains("/fragments/") || normalizedPath.startsWith("fragments/");

        if (!isFragment) {
            Matcher formMatcher = FORM_TAG.matcher(content);
            while (formMatcher.find()) {
                String tagContent = formMatcher.group(1);
                int formStart = formMatcher.start();
                int formOpenEnd = formMatcher.end();

                String action = extractAttr(tagContent, TH_ACTION_ATTR);
                boolean isExpr = false;
                if (action != null) {
                    isExpr = containsExpression(action);
                } else {
                    action = extractAttr(tagContent, PLAIN_ACTION_ATTR);
                    if (action != null) {
                        isExpr = containsExpression(action);
                    }
                }
                if (action == null) {
                    action = "";
                }

                String method = extractAttr(tagContent, TH_METHOD_ATTR);
                if (method == null) {
                    method = extractAttr(tagContent, PLAIN_METHOD_ATTR);
                }
                if (method == null) {
                    method = "GET";
                }
                method = method.toUpperCase();

                int closeTagPos = content.indexOf("</form>", formOpenEnd);
                int formEnd = closeTagPos > 0 ? closeTagPos + "</form>".length() : content.length();

                List<String> fields = new ArrayList<>();
                fields.addAll(TemplateParser.extractFields(content, INPUT_FIELD, formStart, formEnd));
                fields.addAll(TemplateParser.extractFields(content, INPUT_VALUE_SELECT, formStart, formEnd));
                fields.addAll(TemplateParser.extractFields(content, INPUT_VALUE_VAR, formStart, formEnd));
                fields = fields.stream().distinct().toList();

                findings.add(new TemplateFormInfo(method, action, isExpr, fields, "FORM", filePath));
            }
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

    private static String extractAttr(String tagContent, Pattern pattern) {
        Matcher m = pattern.matcher(tagContent);
        return m.find() ? m.group(1) : null;
    }

    private static boolean containsExpression(String url) {
        return url.contains("${") || url.contains("*{");
    }
}
