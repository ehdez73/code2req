package com.github.ehdez73.code2req.analyzer.template;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface TemplateParser {

    boolean supports(Path filePath);

    List<TemplateFormInfo> parse(String content, String filePath);

    static List<String> extractFields(String content, Pattern inputPattern, int formStart, int formEnd) {
        List<String> fields = new java.util.ArrayList<>();
        String formSection = content.substring(formStart, Math.min(formEnd + 500, content.length()));
        Matcher matcher = inputPattern.matcher(formSection);
        while (matcher.find()) {
            fields.add(matcher.group(1));
        }
        return fields;
    }

    static boolean isOverlapping(List<TemplateFormInfo> existing, String filePath) {
        return existing.stream().anyMatch(f ->
            f.templatePath().equals(filePath) && "FORM".equals(f.linkType()));
    }
}