package com.github.ehdez73.code2req.extraction.domain.spi;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UserInteractionServiceTest {

    @Test
    void interfaceDefinesAskMethod() throws NoSuchMethodException {
        Method m = UserInteractionService.class.getMethod("ask", String.class, String.class);
        assertEquals(String.class, m.getReturnType());
    }

    @Test
    void interfaceDefinesConfirmMethod() throws NoSuchMethodException {
        Method m = UserInteractionService.class.getMethod("confirm", String.class);
        assertEquals(boolean.class, m.getReturnType());
    }

    @Test
    void interfaceDefinesSelectMethod() throws NoSuchMethodException {
        Method m = UserInteractionService.class.getMethod("select", List.class, String.class);
        assertEquals(String.class, m.getReturnType());
    }

    @Test
    void interfaceDefinesIsInteractiveDefault() throws NoSuchMethodException {
        Method m = UserInteractionService.class.getMethod("isInteractive");
        assertEquals(boolean.class, m.getReturnType());
    }
}
