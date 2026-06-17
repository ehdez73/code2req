package com.github.ehdez73.code2req.analyzer.web.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class TemplateAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(TemplateAnalyzer.class);

    private final List<TemplateParser> parsers;

    public TemplateAnalyzer(List<TemplateParser> parsers) {
        this.parsers = parsers;
    }

    public List<TemplateFormInfo> analyze(Path templatePath) {
        for (TemplateParser parser : parsers) {
            if (parser.supports(templatePath)) {
                try {
                    String content = Files.readString(templatePath);
                    return parser.parse(content, templatePath.toString());
                } catch (IOException e) {
                    log.warn("Failed to read template {}: {}", templatePath, e.getMessage());
                }
            }
        }
        return List.of();
    }
}