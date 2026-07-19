package com.github.ehdez73.code2req.extraction.adapter.cli;

import com.github.ehdez73.code2req.extraction.domain.spi.UserInteractionService;
import java.util.List;

public class NoOpUserInteractionService implements UserInteractionService {
    @Override
    public String ask(String prompt, String context) {
        return null;
    }

    @Override
    public boolean confirm(String message) {
        return false;
    }

    @Override
    public String select(List<String> options, String prompt) {
        return null;
    }

    @Override
    public boolean isInteractive() {
        return false;
    }
}
