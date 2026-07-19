package com.github.ehdez73.code2req.extraction.adapter.cli;

import com.github.ehdez73.code2req.extraction.domain.spi.UserInteractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;

public class InteractiveUserInteractionService implements UserInteractionService {

    private static final Logger log = LoggerFactory.getLogger(InteractiveUserInteractionService.class);
    private static final String SEPARATOR = "-".repeat(70);
    private static final String PROMPT_PREFIX = "[Ambiguity]";
    private static final String WRITE_OWN_OPTION = "None of these — provide my own answer";

    private final BufferedReader reader;
    private final PrintWriter writer;

    public InteractiveUserInteractionService() {
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.writer = new PrintWriter(System.out, true);
    }

    @Override
    public String ask(String prompt, String context) {
        writer.printf("\n%s\n", SEPARATOR);
        writer.printf("%s %s\n\n", PROMPT_PREFIX, prompt);
        if (context != null && !context.isBlank()) {
            writer.printf("%s\n\n", context);
        }
        writer.printf("> ");
        writer.flush();
        return readLine();
    }

    @Override
    public boolean confirm(String message) {
        writer.printf("\n%s\n", SEPARATOR);
        writer.printf("%s %s (y/n)\n", PROMPT_PREFIX, message);
        writer.printf("> ");
        writer.flush();
        String input = readLine();
        return "y".equalsIgnoreCase(input != null ? input.trim() : "");
    }

    @Override
    public String select(List<String> options, String prompt) {
        writer.printf("\n%s\n", SEPARATOR);
        writer.printf("%s %s\n\n", PROMPT_PREFIX, prompt);
        for (int i = 0; i < options.size(); i++) {
            writer.printf("  %d. %s\n", i + 1, options.get(i));
        }
        writer.printf("  %d. %s\n", options.size() + 1, WRITE_OWN_OPTION);
        writer.printf("\n> ");
        writer.flush();
        String input = readLine();
        if (input == null) return null;

        try {
            int choice = Integer.parseInt(input.trim());
            if (choice >= 1 && choice <= options.size()) {
                return options.get(choice - 1);
            }
            if (choice == options.size() + 1) {
                return promptForCustomAnswer();
            }
        } catch (NumberFormatException e) {
            return input.trim();
        }
        return input.trim();
    }

    private String promptForCustomAnswer() {
        writer.printf("\n%s\n", SEPARATOR);
        writer.printf("Provide additional context the LLM should consider:\n> ");
        writer.flush();
        return readLine();
    }

    private String readLine() {
        try {
            return reader.readLine();
        } catch (IOException e) {
            log.warn("Failed to read from stdin: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isInteractive() {
        return true;
    }
}
