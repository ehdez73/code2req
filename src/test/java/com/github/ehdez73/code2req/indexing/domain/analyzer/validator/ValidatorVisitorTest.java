package com.github.ehdez73.code2req.indexing.domain.analyzer.validator;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorVisitorTest {

    private ParserConfiguration.LanguageLevel previousLevel;

    @BeforeEach
    void setUp() {
        previousLevel = StaticJavaParser.getConfiguration().getLanguageLevel();
        StaticJavaParser.getConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    @AfterEach
    void tearDown() {
        StaticJavaParser.getConfiguration().setLanguageLevel(previousLevel);
    }

    private final ValidatorVisitor visitor = new ValidatorVisitor();

    private AnalysisResult analyze(String filePath, String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        visitor.analyze(cu, builder, new AnalysisContext(filePath));
        return builder.build(filePath);
    }

    @Test
    void extractsCustomConstraintValidatorIsValidBody() {
        AnalysisResult result = analyze("PaymentValidator.java", """
            import jakarta.validation.ConstraintValidator;
            import jakarta.validation.ConstraintValidatorContext;
            public class PaymentValidator implements ConstraintValidator<ValidPayment, Payment> {
                @Override
                public boolean isValid(Payment value, ConstraintValidatorContext context) {
                    return value.getAmount() > 0;
                }
            }
            """);

        assertEquals(1, result.findings(ValidatorInfo.class).size());
        ValidatorInfo vi = result.findings(ValidatorInfo.class).getFirst();
        assertEquals("PaymentValidator", vi.className());
        assertEquals("isValid", vi.elementName());
        assertTrue(vi.isValidBody().contains("return value.getAmount() > 0;"));
        assertFalse(vi.isBuiltIn());
        assertEquals("Constraint", vi.annotationType());
    }

    @Test
    void extractsIsValidBodyWithMultipleStatements() {
        AnalysisResult result = analyze("UserValidator.java", """
            import jakarta.validation.ConstraintValidator;
            import jakarta.validation.ConstraintValidatorContext;
            public class UserValidator implements ConstraintValidator<ValidUser, User> {
                @Override
                public boolean isValid(User user, ConstraintValidatorContext context) {
                    if (user.getName() == null) {
                        return false;
                    }
                    return user.getName().length() > 2;
                }
            }
            """);

        assertEquals(1, result.findings(ValidatorInfo.class).size());
        ValidatorInfo vi = result.findings(ValidatorInfo.class).getFirst();
        assertTrue(vi.isValidBody().contains("if (user.getName() == null)"));
        assertTrue(vi.isValidBody().contains("return user.getName().length() > 2;"));
        assertFalse(vi.isBuiltIn());
    }

    @Test
    void noIsValidMethod_producesNoCustomValidator() {
        AnalysisResult result = analyze("PlainService.java", """
            import org.springframework.stereotype.Service;
            @Service
            public class PlainService {
                public void doSomething() {}
            }
            """);

        assertTrue(result.findings(ValidatorInfo.class).isEmpty());
    }

    @Test
    void notesBuiltInFieldValidationAnnotations() {
        AnalysisResult result = analyze("User.java", """
            import jakarta.validation.constraints.NotNull;
            import jakarta.validation.constraints.Size;
            public class User {
                @NotNull
                @Size(min = 2, max = 100)
                private String name;
            }
            """);

        assertEquals(2, result.findings(ValidatorInfo.class).size());
        assertTrue(result.findings(ValidatorInfo.class).stream().allMatch(ValidatorInfo::isBuiltIn));

        ValidatorInfo notNull = result.findings(ValidatorInfo.class).stream()
            .filter(v -> v.annotationType().equals("NotNull"))
            .findFirst().orElseThrow();
        assertEquals("name", notNull.elementName());
        assertTrue(notNull.isValidBody().isEmpty());

        ValidatorInfo size = result.findings(ValidatorInfo.class).stream()
            .filter(v -> v.annotationType().equals("Size"))
            .findFirst().orElseThrow();
        assertEquals("name", size.elementName());
    }

    @Test
    void handlesMultipleFieldsWithDifferentAnnotations() {
        AnalysisResult result = analyze("Order.java", """
            import jakarta.validation.constraints.NotNull;
            import jakarta.validation.constraints.Email;
            import jakarta.validation.constraints.Positive;
            public class Order {
                @NotNull
                private String id;
                @Email
                private String email;
                @Positive
                private double amount;
            }
            """);

        assertEquals(3, result.findings(ValidatorInfo.class).size());
        assertEquals(3, result.findings(ValidatorInfo.class).stream()
            .filter(ValidatorInfo::isBuiltIn).count());
    }

    @Test
    void classWithBothCustomAndBuiltInAnnotations() {
        AnalysisResult result = analyze("OrderValidator.java", """
            import jakarta.validation.ConstraintValidator;
            import jakarta.validation.ConstraintValidatorContext;
            import jakarta.validation.constraints.NotNull;
            public class OrderValidator implements ConstraintValidator<ValidOrder, Order> {
                @NotNull
                private String errorMessage;
                @Override
                public boolean isValid(Order order, ConstraintValidatorContext context) {
                    return order != null;
                }
            }
            """);

        assertEquals(2, result.findings(ValidatorInfo.class).size());

        ValidatorInfo custom = result.findings(ValidatorInfo.class).stream()
            .filter(v -> !v.isBuiltIn())
            .findFirst().orElseThrow();
        assertEquals("OrderValidator", custom.className());
        assertTrue(custom.isValidBody().contains("return order != null;"));

        ValidatorInfo builtIn = result.findings(ValidatorInfo.class).stream()
            .filter(ValidatorInfo::isBuiltIn)
            .findFirst().orElseThrow();
        assertEquals("NotNull", builtIn.annotationType());
        assertEquals("errorMessage", builtIn.elementName());
    }

    @Test
    void noAnnotationsOnFields_producesNoBuiltInFindings() {
        AnalysisResult result = analyze("PlainModel.java", """
            public class PlainModel {
                private String name;
                private int age;
            }
            """);

        assertTrue(result.findings(ValidatorInfo.class).isEmpty());
    }

    @Test
    void classWithMultipleIsValidMethods_allCaptured() {
        AnalysisResult result = analyze("MultiValidator.java", """
            import jakarta.validation.ConstraintValidator;
            import jakarta.validation.ConstraintValidatorContext;
            public class MultiValidator {
                public boolean isValid(String s, ConstraintValidatorContext ctx) {
                    return s != null;
                }
                public boolean isValid(Integer i, ConstraintValidatorContext ctx) {
                    return i > 0;
                }
            }
            """);

        assertEquals(2, result.findings(ValidatorInfo.class).size());
        assertEquals(2, result.findings(ValidatorInfo.class).stream()
            .filter(v -> !v.isBuiltIn())
            .count());
    }

    @Test
    void nestedClassWithIsValid_isCaptured() {
        AnalysisResult result = analyze("Outer.java", """
            public class Outer {
                public class InnerValidator {
                    public boolean isValid(String value, jakarta.validation.ConstraintValidatorContext ctx) {
                        return value != null;
                    }
                }
            }
            """);

        assertEquals(1, result.findings(ValidatorInfo.class).size());
        ValidatorInfo vi = result.findings(ValidatorInfo.class).getFirst();
        assertEquals("InnerValidator", vi.className());
        assertFalse(vi.isBuiltIn());
    }

    @Test
    void notesJakartaValidAnnotation() {
        AnalysisResult result = analyze("User.java", """
            import jakarta.validation.Valid;
            public class User {
                @Valid
                private Address address;
            }
            """);

        assertEquals(1, result.findings(ValidatorInfo.class).size());
        ValidatorInfo vi = result.findings(ValidatorInfo.class).getFirst();
        assertEquals("Valid", vi.annotationType());
        assertTrue(vi.isBuiltIn());
        assertEquals("address", vi.elementName());
    }

    @Test
    void notesJavaxValidAnnotation() {
        AnalysisResult result = analyze("User.java", """
            import javax.validation.Valid;
            public class User {
                @Valid
                private Address address;
            }
            """);

        assertEquals(1, result.findings(ValidatorInfo.class).size());
        ValidatorInfo vi = result.findings(ValidatorInfo.class).getFirst();
        assertEquals("Valid", vi.annotationType());
        assertTrue(vi.isBuiltIn());
        assertEquals("address", vi.elementName());
    }

    @Test
    void unregisteredAnnotationName_notCaptured() {
        AnalysisResult result = analyze("Random.java", """
            public class Random {
                @SomeRandomAnnotation
                private String field;
            }
            """);

        assertTrue(result.findings(ValidatorInfo.class).isEmpty());
    }

    @Test
    void extractsBuiltInAnnotationsOnRecordComponents() {
        AnalysisResult result = analyze("User.java", """
            import jakarta.validation.constraints.NotBlank;
            import jakarta.validation.constraints.Email;
            public record User(
                @NotBlank(message = "Name is mandatory")
                String name,
                @Email(message = "Email should be valid")
                String email) {}
            """);

        assertEquals(2, result.findings(ValidatorInfo.class).size());
        assertTrue(result.findings(ValidatorInfo.class).stream().allMatch(ValidatorInfo::isBuiltIn));

        ValidatorInfo notBlank = result.findings(ValidatorInfo.class).stream()
            .filter(v -> v.annotationType().equals("NotBlank"))
            .findFirst().orElseThrow();
        assertEquals("User", notBlank.className());
        assertEquals("name", notBlank.elementName());

        ValidatorInfo email = result.findings(ValidatorInfo.class).stream()
            .filter(v -> v.annotationType().equals("Email"))
            .findFirst().orElseThrow();
        assertEquals("User", email.className());
        assertEquals("email", email.elementName());
    }

    @Test
    void extractsBuiltInAnnotationsOnNestedRecord() {
        AnalysisResult result = analyze("HelloController.java", """
            import jakarta.validation.constraints.NotBlank;
            import jakarta.validation.constraints.Size;
            import org.springframework.web.bind.annotation.*;
            @RestController
            public class HelloController {
                @PostMapping("/user")
                public String helloUser(@RequestBody User user) {
                    return user.name();
                }
                public record User(
                    @NotBlank(message = "Name is mandatory")
                    @Size(min = 2, max = 30)
                    String name) {}
            }
            """);

        assertEquals(2, result.findings(ValidatorInfo.class).size());
        assertTrue(result.findings(ValidatorInfo.class).stream().allMatch(ValidatorInfo::isBuiltIn));

        ValidatorInfo notBlank = result.findings(ValidatorInfo.class).stream()
            .filter(v -> v.annotationType().equals("NotBlank"))
            .findFirst().orElseThrow();
        assertEquals("User", notBlank.className());
        assertEquals("name", notBlank.elementName());

        ValidatorInfo size = result.findings(ValidatorInfo.class).stream()
            .filter(v -> v.annotationType().equals("Size"))
            .findFirst().orElseThrow();
        assertEquals("User", size.className());
        assertEquals("name", size.elementName());
    }

    @Test
    void recordWithoutAnnotations_producesNoFindings() {
        AnalysisResult result = analyze("Empty.java", """
            public record Empty(String name, String email) {}
            """);

        assertTrue(result.findings(ValidatorInfo.class).isEmpty());
    }

    @Test
    void handlesMixOfRecordAndRegularClass() {
        AnalysisResult result = analyze("Mixed.java", """
            import jakarta.validation.constraints.NotBlank;
            import jakarta.validation.constraints.Email;
            public record Dto(@NotBlank String name) {}
            class Regular {
                @Email
                private String email;
            }
            """);

        assertEquals(2, result.findings(ValidatorInfo.class).size());
        assertTrue(result.findings(ValidatorInfo.class).stream().allMatch(ValidatorInfo::isBuiltIn));

        ValidatorInfo dtoNotBlank = result.findings(ValidatorInfo.class).stream()
            .filter(v -> v.annotationType().equals("NotBlank"))
            .findFirst().orElseThrow();
        assertEquals("Dto", dtoNotBlank.className());
        assertEquals("name", dtoNotBlank.elementName());

        ValidatorInfo regularEmail = result.findings(ValidatorInfo.class).stream()
            .filter(v -> v.annotationType().equals("Email"))
            .findFirst().orElseThrow();
        assertEquals("Regular", regularEmail.className());
        assertEquals("email", regularEmail.elementName());
    }
}
