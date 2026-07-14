package com.github.ehdez73.code2req.indexing.domain.service;

import com.github.javaparser.ParserConfiguration;

public class JavaVersionMapper {

    public static ParserConfiguration.LanguageLevel toLanguageLevel(String version) {
        if (version == null || version.isBlank()) {
            return ParserConfiguration.LanguageLevel.JAVA_17;
        }
        return switch (version.strip()) {
            case "8" -> ParserConfiguration.LanguageLevel.JAVA_8;
            case "11" -> ParserConfiguration.LanguageLevel.JAVA_11;
            case "14" -> ParserConfiguration.LanguageLevel.JAVA_14;
            case "15" -> ParserConfiguration.LanguageLevel.JAVA_15;
            case "16" -> ParserConfiguration.LanguageLevel.JAVA_16;
            case "17" -> ParserConfiguration.LanguageLevel.JAVA_17;
            case "18" -> ParserConfiguration.LanguageLevel.JAVA_18;
            default -> ParserConfiguration.LanguageLevel.JAVA_17;
        };
    }
}
