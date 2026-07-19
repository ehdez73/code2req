package com.github.ehdez73.code2req.extraction.adapter.cli;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NoOpUserInteractionServiceTest {

    private final NoOpUserInteractionService service = new NoOpUserInteractionService();

    @Test
    void askReturnsNull() {
        assertNull(service.ask("What is this?", "context"));
    }

    @Test
    void confirmReturnsFalse() {
        assertFalse(service.confirm("Is this correct?"));
    }

    @Test
    void selectReturnsNull() {
        assertNull(service.select(List.of("A", "B"), "Choose one"));
    }

    @Test
    void isInteractiveReturnsFalse() {
        assertFalse(service.isInteractive());
    }
}
