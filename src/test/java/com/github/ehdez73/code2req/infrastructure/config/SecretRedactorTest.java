package com.github.ehdez73.code2req.infrastructure.config;

import com.github.ehdez73.code2req.indexing.domain.service.RedactionResult;
import com.github.ehdez73.code2req.indexing.domain.service.SecretRedactor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretRedactorTest {

    private SecretRedactor redactor;

    @BeforeEach
    void setUp() {
        redactor = new SecretRedactor();
    }

    @Test
    void hardcodedPasswordIsRedacted() {
        String input = "private String password = \"supersecret123\";";
        String result = redactor.redact(input);
        assertTrue(result.contains("[REDACTED:password]"));
        assertFalse(result.contains("supersecret123"));
    }

    @Test
    void apiKeyIsRedacted() {
        String input = "private String apiKey = \"sk-abc123def456\";";
        String result = redactor.redact(input);
        assertTrue(result.contains("[REDACTED:api_key]"));
        assertFalse(result.contains("sk-abc123def456"));
    }

    @Test
    void multipleSecretTypesAreAllRedacted() {
        String input = """
            private String password = "hunter2";
            private String apiKey = "abc123";
            private String token = "eyJ.eyJ.dG9r";
            """;
        String result = redactor.redact(input);
        assertTrue(result.contains("[REDACTED:password]"));
        assertTrue(result.contains("[REDACTED:api_key]"));
        assertTrue(result.contains("[REDACTED:token]"));
        assertFalse(result.contains("hunter2"));
        assertFalse(result.contains("abc123"));
        assertFalse(result.contains("eyJ.eyJ.dG9r"));
    }

    @Test
    void nonSecretValuesArePreserved() {
        String input = """
            private String name = "John";
            private int port = 8080;
            private String url = "http://example.com";
            """;
        String result = redactor.redact(input);
        assertTrue(result.contains("John"));
        assertTrue(result.contains("8080"));
        assertTrue(result.contains("http://example.com"));
    }

    @Test
    void apiKeyAlternateFormats() {
        String input = """
            private String api_key = "key1";
            private String apikey = "key2";
            """;
        String result = redactor.redact(input);
        assertTrue(result.contains("[REDACTED:api_key]"));
        assertFalse(result.contains("key1"));
        assertFalse(result.contains("key2"));
    }

    @Test
    void connectionStringRedacted() {
        String input = "jdbc:mysql://admin:secret123@localhost:3306/mydb";
        String result = redactor.redact(input);
        assertTrue(result.contains("[REDACTED:credentials]"));
        assertFalse(result.contains("admin:secret123"));
        assertFalse(result.contains("admin"));
    }

    @Test
    void emptyStringReturnsEmpty() {
        assertEquals("", redactor.redact(""));
    }

    @Test
    void contentWithNoSecretsUnchanged() {
        String input = "public class Hello { public void greet() { System.out.println(\"Hi\"); } }";
        assertEquals(input, redactor.redact(input));
    }

    @Test
    void redactWithResultReturnsAccurateCounts() {
        String input = """
            String password = "p1";
            String password = "p2";
            String token = "t1";
            """;
        RedactionResult result = redactor.redactWithResult(input);
        assertEquals(Integer.valueOf(2), result.counts().get("password"));
        assertEquals(Integer.valueOf(1), result.counts().get("token"));
        assertEquals(3, result.totalRedactions());
    }

    @Test
    void nullContentReturnsEmptyResult() {
        RedactionResult result = redactor.redactWithResult(null);
        assertNull(result.redactedContent());
        assertTrue(result.counts().isEmpty());
        assertEquals(0, result.totalRedactions());
    }
}
