package com.github.ehdez73.code2req.indexing.domain.service;

import com.github.javaparser.ParserConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class JavaVersionMapperTest {

    @ParameterizedTest
    @CsvSource({
        "8, JAVA_8",
        "11, JAVA_11",
        "14, JAVA_14",
        "15, JAVA_15",
        "16, JAVA_16",
        "17, JAVA_17",
        "18, JAVA_18"
    })
    void mapsKnownVersions(String version, String expected) {
        assertEquals(
            ParserConfiguration.LanguageLevel.valueOf(expected),
            JavaVersionMapper.toLanguageLevel(version));
    }

    @Test
    void defaultsToJava17ForUnknownVersion() {
        assertEquals(
            ParserConfiguration.LanguageLevel.JAVA_17,
            JavaVersionMapper.toLanguageLevel("21"));
    }

    @Test
    void defaultsToJava17ForNull() {
        assertEquals(
            ParserConfiguration.LanguageLevel.JAVA_17,
            JavaVersionMapper.toLanguageLevel(null));
    }

    @Test
    void defaultsToJava17ForBlank() {
        assertEquals(
            ParserConfiguration.LanguageLevel.JAVA_17,
            JavaVersionMapper.toLanguageLevel("  "));
    }

    @Test
    void trimsInputVersion() {
        assertEquals(
            ParserConfiguration.LanguageLevel.JAVA_17,
            JavaVersionMapper.toLanguageLevel(" 17 "));
    }
}
