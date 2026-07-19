package com.github.ehdez73.code2req.extraction.adapter.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InteractiveUserInteractionServiceTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final java.io.InputStream originalIn = System.in;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(out, true));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setIn(originalIn);
    }

    @Test
    void isInteractiveReturnsTrue() {
        var service = new InteractiveUserInteractionService();
        assertTrue(service.isInteractive());
    }

    @Test
    void askReadsUserResponse() {
        System.setIn(new ByteArrayInputStream("Stripe gateway\n".getBytes()));
        var service = new InteractiveUserInteractionService();

        String result = service.ask("Which payment gateway?", "context");

        assertEquals("Stripe gateway", result);
        assertTrue(out.toString().contains("Which payment gateway?"));
    }

    @Test
    void confirmReturnsTrueForY() {
        System.setIn(new ByteArrayInputStream("y\n".getBytes()));
        var service = new InteractiveUserInteractionService();

        assertTrue(service.confirm("Is this correct?"));
    }

    @Test
    void confirmReturnsFalseForN() {
        System.setIn(new ByteArrayInputStream("n\n".getBytes()));
        var service = new InteractiveUserInteractionService();

        assertFalse(service.confirm("Is this correct?"));
    }

    @Test
    void confirmReturnsFalseForEmptyInput() {
        System.setIn(new ByteArrayInputStream("\n".getBytes()));
        var service = new InteractiveUserInteractionService();

        assertFalse(service.confirm("Is this correct?"));
    }

    @Test
    void selectReturnsChosenOption() {
        System.setIn(new ByteArrayInputStream("2\n".getBytes()));
        var service = new InteractiveUserInteractionService();

        String result = service.select(List.of("Paypal", "Stripe"), "Choose gateway");

        assertEquals("Stripe", result);
        assertTrue(out.toString().contains("Paypal"));
        assertTrue(out.toString().contains("Stripe"));
        assertTrue(out.toString().contains("None of these"));
    }

    @Test
    void selectWriteOwnOptionPromptsForAnswer() {
        System.setIn(new ByteArrayInputStream("3\nCustom gateway\n".getBytes()));
        var service = new InteractiveUserInteractionService();

        String result = service.select(List.of("Paypal", "Stripe"), "Choose gateway");

        assertEquals("Custom gateway", result);
        assertTrue(out.toString().contains("Provide additional context"));
    }

    @Test
    void selectReturnsRawInputWhenNotNumeric() {
        System.setIn(new ByteArrayInputStream("Custom answer\n".getBytes()));
        var service = new InteractiveUserInteractionService();

        String result = service.select(List.of("A", "B"), "Pick one");

        assertEquals("Custom answer", result);
    }

    @Test
    void selectShowsWriteOwnOption() {
        System.setIn(new ByteArrayInputStream("1\n".getBytes()));
        var service = new InteractiveUserInteractionService();

        service.select(List.of("A", "B"), "prompt");

        assertTrue(out.toString().contains("None of these"));
    }
}
