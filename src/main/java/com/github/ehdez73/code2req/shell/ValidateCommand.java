package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.config.ManifestValidationResult;
import com.github.ehdez73.code2req.config.ManifestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ShellComponent
public class ValidateCommand {


    Logger log = LoggerFactory.getLogger(ValidateCommand.class);

    private final ManifestValidator manifestValidator;

    public ValidateCommand(ManifestValidator manifestValidator) {
        this.manifestValidator = manifestValidator;
    }

    @ShellMethod(key = "validate", value = "Validates a project manifest YAML file")
    public String validate(
            @ShellOption(value = "--manifest", defaultValue = "project-manifest.yaml",
                         help = "Path to the project manifest YAML file") String manifestPath) {

        Path path = Path.of(manifestPath);

        log.info("Starting manifest validation for: {}", path.toAbsolutePath());


        if (!Files.exists(path)) {
            return "Error: Manifest file not found: " + manifestPath;
        }

        try {
            ManifestValidationResult result = manifestValidator.validate(path);
            StringBuilder output = new StringBuilder(result.summary());

            if (result.hasErrors()) {
                output.append("\nErrors:");
                for (String err : result.getErrors()) {
                    output.append("\n  - ").append(err);
                }
            }

            if (result.hasWarnings()) {
                output.append("\nWarnings:");
                for (String warn : result.getWarnings()) {
                    output.append("\n  - ").append(warn);
                }
            }

            return output.toString();
        } catch (IOException e) {
            return "Error: Failed to read manifest: " + e.getMessage();
        }
    }
}
