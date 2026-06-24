package com.github.ehdez73.code2req.executor.testmining;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TestAssertionExtractor {

    private static final Pattern ASSERT_EQUALS_PATTERN = Pattern.compile(
        "assertEquals\\s*\\(([^,]+),\\s*([^)]+)\\)",
        Pattern.DOTALL);

    private static final Pattern ASSERT_THROWS_PATTERN = Pattern.compile(
        "assertThrows\\s*\\(\\s*([\\w.]+)\\s*\\.\\s*class\\s*[,)]",
        Pattern.DOTALL);

    private static final Pattern ASSERT_TRUE_PATTERN = Pattern.compile(
        "assertTrue\\s*\\(([^)]+)\\)",
        Pattern.DOTALL);

    private static final Pattern ASSERT_FALSE_PATTERN = Pattern.compile(
        "assertFalse\\s*\\(([^)]+)\\)",
        Pattern.DOTALL);

    private static final Pattern ASSERT_NULL_PATTERN = Pattern.compile(
        "assertNull\\s*\\(([^)]+)\\)",
        Pattern.DOTALL);

    private static final Pattern ASSERT_NOT_NULL_PATTERN = Pattern.compile(
        "assertNotNull\\s*\\(([^)]+)\\)",
        Pattern.DOTALL);

    private static final Pattern ASSERT_SAME_PATTERN = Pattern.compile(
        "assertSame\\s*\\(([^,]+),\\s*([^)]+)\\)",
        Pattern.DOTALL);

    private static final Pattern ASSERT_NOT_SAME_PATTERN = Pattern.compile(
        "assertNotSame\\s*\\(([^,]+),\\s*([^)]+)\\)",
        Pattern.DOTALL);

    private static final Pattern ASSERT_ARRAY_EQUALS_PATTERN = Pattern.compile(
        "assertArrayEquals\\s*\\(([^)]+)\\)",
        Pattern.DOTALL);

    private static final Pattern ASSERT_THAT_PATTERN = Pattern.compile(
        "assertThat\\s*\\(([^,]+),\\s*([^)]+)\\)",
        Pattern.DOTALL);

    private static final Pattern FAIL_PATTERN = Pattern.compile(
        "fail\\s*\\(\"([^\"]*)\"\\)",
        Pattern.DOTALL);

    private static final Pattern VERIFY_PATTERN = Pattern.compile(
        "verify\\s*\\(([^)]+)\\)(?:\\.\\s*([\\w]+)\\s*\\([^)]*\\))?",
        Pattern.DOTALL);

    private static final Pattern EXPECTED_EXCEPTION_PATTERN = Pattern.compile(
        "@Test\\s*\\(\\s*expected\\s*=\\s*([\\w.]+)\\s*\\.\\s*class\\s*\\)",
        Pattern.DOTALL);

    public List<AssertionInfo> extract(String testContent) {
        List<AssertionInfo> assertions = new ArrayList<>();

        if (testContent == null || testContent.isBlank()) {
            return assertions;
        }

        extractPattern(testContent, ASSERT_EQUALS_PATTERN, "assertEquals", assertions);
        extractPattern(testContent, ASSERT_THROWS_PATTERN, "assertThrows", assertions);
        extractPattern(testContent, ASSERT_TRUE_PATTERN, "assertTrue", assertions);
        extractPattern(testContent, ASSERT_FALSE_PATTERN, "assertFalse", assertions);
        extractPattern(testContent, ASSERT_NULL_PATTERN, "assertNull", assertions);
        extractPattern(testContent, ASSERT_NOT_NULL_PATTERN, "assertNotNull", assertions);
        extractPattern(testContent, ASSERT_SAME_PATTERN, "assertSame", assertions);
        extractPattern(testContent, ASSERT_NOT_SAME_PATTERN, "assertNotSame", assertions);
        extractPattern(testContent, ASSERT_ARRAY_EQUALS_PATTERN, "assertArrayEquals", assertions);
        extractPattern(testContent, ASSERT_THAT_PATTERN, "assertThat", assertions);
        extractFailPattern(testContent, assertions);
        extractVerifyPattern(testContent, assertions);
        extractExpectedExceptionPattern(testContent, assertions);

        return assertions;
    }

    private void extractPattern(String content, Pattern pattern, String type, List<AssertionInfo> result) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String args = matcher.groupCount() >= 2
                ? matcher.group(1) + ", " + matcher.group(2)
                : matcher.group(1);
            result.add(new AssertionInfo(type, args.trim(), describeAssertion(type, args)));
        }
    }

    private void extractFailPattern(String content, List<AssertionInfo> result) {
        Matcher matcher = FAIL_PATTERN.matcher(content);
        while (matcher.find()) {
            String message = matcher.group(1);
            result.add(new AssertionInfo("fail", message, "Test explicitly fails: " + message));
        }
    }

    private void extractVerifyPattern(String content, List<AssertionInfo> result) {
        Matcher matcher = VERIFY_PATTERN.matcher(content);
        while (matcher.find()) {
            String mock = matcher.group(1).trim();
            String methodCall = matcher.group(2) != null ? "." + matcher.group(2) + "(...)" : "";
            result.add(new AssertionInfo("verify", mock + methodCall,
                "Verifies interaction with " + mock));
        }
    }

    private void extractExpectedExceptionPattern(String content, List<AssertionInfo> result) {
        Matcher matcher = EXPECTED_EXCEPTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String exceptionType = matcher.group(1).trim();
            result.add(new AssertionInfo("expectedException", exceptionType,
                "Test expects " + exceptionType + " to be thrown"));
        }
    }

    private String describeAssertion(String type, String args) {
        return switch (type) {
            case "assertEquals" -> "Asserts equality: " + args;
            case "assertThrows" -> "Expects exception: " + args;
            case "assertTrue" -> "Asserts condition is true: " + args;
            case "assertFalse" -> "Asserts condition is false: " + args;
            case "assertNull" -> "Asserts value is null: " + args;
            case "assertNotNull" -> "Asserts value is not null: " + args;
            case "assertSame" -> "Asserts same reference: " + args;
            case "assertNotSame" -> "Asserts different reference: " + args;
            case "assertArrayEquals" -> "Asserts array equality: " + args;
            case "assertThat" -> "Asserts with matcher: " + args;
            default -> args;
        };
    }

    public record AssertionInfo(
        String type,
        String detail,
        String description
    ) {}
}
