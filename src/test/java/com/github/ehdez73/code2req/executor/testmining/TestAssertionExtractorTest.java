package com.github.ehdez73.code2req.executor.testmining;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestAssertionExtractorTest {

    private TestAssertionExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new TestAssertionExtractor();
    }

    @Nested
    class JUnitAssertions {

        @Test
        void extractsAssertEquals() {
            var result = extractor.extract("assertEquals(42, result);");
            assertEquals(1, result.size());
            assertEquals("assertEquals", result.get(0).type());
        }

        @Test
        void extractsAssertTrue() {
            var result = extractor.extract("assertTrue(result.isPresent());");
            assertEquals(1, result.size());
            assertEquals("assertTrue", result.get(0).type());
        }

        @Test
        void extractsAssertFalse() {
            var result = extractor.extract("assertFalse(list.isEmpty());");
            assertEquals(1, result.size());
            assertEquals("assertFalse", result.get(0).type());
        }

        @Test
        void extractsAssertNull() {
            var result = extractor.extract("assertNull(result);");
            assertEquals(1, result.size());
            assertEquals("assertNull", result.get(0).type());
        }

        @Test
        void extractsAssertNotNull() {
            var result = extractor.extract("assertNotNull(entity);");
            assertEquals(1, result.size());
            assertEquals("assertNotNull", result.get(0).type());
        }

        @Test
        void extractsAssertSame() {
            var result = extractor.extract("assertSame(expected, actual);");
            assertEquals(1, result.size());
            assertEquals("assertSame", result.get(0).type());
        }

        @Test
        void extractsAssertNotSame() {
            var result = extractor.extract("assertNotSame(a, b);");
            assertEquals(1, result.size());
            assertEquals("assertNotSame", result.get(0).type());
        }

        @Test
        void extractsAssertArrayEquals() {
            var result = extractor.extract("assertArrayEquals(expected, actual);");
            assertEquals(1, result.size());
            assertEquals("assertArrayEquals", result.get(0).type());
        }

        @Test
        void extractsFail() {
            var result = extractor.extract("fail(\"Should not reach here\");");
            assertEquals(1, result.size());
            assertEquals("fail", result.get(0).type());
        }

        @Test
        void extractsAssertThrows() {
            var result = extractor.extract("assertThrows(IllegalArgumentException.class, () -> service.process(null));");
            assertEquals(1, result.size());
            assertEquals("assertThrows", result.get(0).type());
        }

        @Test
        void extractsExpectedExceptionAnnotation() {
            var result = extractor.extract("@Test(expected = IllegalArgumentException.class)");
            assertEquals(1, result.size());
            assertEquals("expectedException", result.get(0).type());
        }
    }

    @Nested
    class HamcrestAssertions {

        @Test
        void extractsAssertThat() {
            var result = extractor.extract("assertThat(result, is(equalTo(42)));");
            assertEquals(1, result.size());
            assertEquals("assertThat", result.get(0).type());
        }
    }

    @Nested
    class MockitoVerifications {

        @Test
        void extractsVerify() {
            var result = extractor.extract("verify(orderService).createOrder(any());");
            assertEquals(1, result.size());
            assertEquals("verify", result.get(0).type());
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void nullContentReturnsEmpty() {
            assertTrue(extractor.extract(null).isEmpty());
        }

        @Test
        void blankContentReturnsEmpty() {
            assertTrue(extractor.extract("  ").isEmpty());
        }

        @Test
        void noAssertionsReturnsEmpty() {
            var result = extractor.extract("public class FooTest { }");
            assertTrue(result.isEmpty());
        }

        @Test
        void multipleAssertionsAllExtracted() {
            var testCode = """
                assertEquals(200, response.status());
                assertNotNull(response.body());
                assertTrue(response.body().contains("success"));
                assertThrows(TimeoutException.class, () -> client.call());
                verify(service).process(any());
                """;
            var result = extractor.extract(testCode);
            assertEquals(5, result.size());
        }
    }
}
