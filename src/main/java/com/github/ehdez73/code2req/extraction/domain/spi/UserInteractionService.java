package com.github.ehdez73.code2req.extraction.domain.spi;

import java.util.List;

public interface UserInteractionService {
    String ask(String prompt, String context);
    boolean confirm(String message);
    String select(List<String> options, String prompt);
    default boolean isInteractive() { return false; }
}
