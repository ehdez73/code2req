package com.github.ehdez73.code2req.infrastructure.cli.command;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class RunCommand {

    private static final String SUGGESTED_NEXT = "\n=== Suggested Next ===";

    private final ScanCommand scanCommand;
    private final ExtractCommand extractCommand;
    private final GenerateCommand generateCommand;

    public RunCommand(ScanCommand scanCommand,
                     ExtractCommand extractCommand,
                     GenerateCommand generateCommand) {
        this.scanCommand = scanCommand;
        this.extractCommand = extractCommand;
        this.generateCommand = generateCommand;
    }

    @ShellMethod(key = "run", value = "Execute the full pipeline: scan -> extract -> generate")
    public String run(
            @ShellOption(value = "--manifest", defaultValue = "project-manifest.yaml",
                         help = "Path to the project manifest YAML file") String manifestPath,
            @ShellOption(value = "--resume", defaultValue = "false",
                         help = "Resume mode for scan and extract") boolean resume,
            @ShellOption(value = "--dry-run", defaultValue = "false",
                         help = "Dry-run mode for extract") boolean dryRun,
            @ShellOption(value = "--force", defaultValue = "false",
                         help = "Force re-execution for Phase 3 extraction") boolean force) {

        var sb = new StringBuilder();
        sb.append("=== Full Pipeline Run ===\n\n");

        sb.append(stripSuggestions(scanCommand.executeScan(manifestPath, resume))).append("\n");
        sb.append(stripSuggestions(extractCommand.extract(manifestPath, dryRun, force, resume))).append("\n");
        sb.append(stripSuggestions(generateCommand.generate())).append("\n");

        sb.append("=== Full Pipeline Complete ===");
        return sb.toString();
    }

    private static String stripSuggestions(String output) {
        int idx = output.indexOf(SUGGESTED_NEXT);
        return idx >= 0 ? output.substring(0, idx) : output;
    }
}
