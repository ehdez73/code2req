package com.github.ehdez73.code2req.infrastructure.cli.command;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class RunCommand {

    private static final String SUGGESTED_NEXT = "\n=== Suggested Next ===";

    private final ScanCommand scanCommand;
    private final PlanCommand planCommand;
    private final EnrichCommand enrichCommand;
    private final ExtractCommand extractCommand;
    private final GenerateCommand generateCommand;

    public RunCommand(ScanCommand scanCommand, PlanCommand planCommand,
                     EnrichCommand enrichCommand, ExtractCommand extractCommand,
                     GenerateCommand generateCommand) {
        this.scanCommand = scanCommand;
        this.planCommand = planCommand;
        this.enrichCommand = enrichCommand;
        this.extractCommand = extractCommand;
        this.generateCommand = generateCommand;
    }

    @ShellMethod(key = "run", value = "Execute the full pipeline: scan -> plan -> enrich -> extract -> generate")
    public String run(
            @ShellOption(value = "--manifest", defaultValue = "project-manifest.yaml",
                         help = "Path to the project manifest YAML file") String manifestPath,
            @ShellOption(value = "--resume", defaultValue = "false",
                         help = "Resume mode for scan, enrich, and extract") boolean resume,
            @ShellOption(value = "--dry-run", defaultValue = "false",
                         help = "Dry-run mode for enrich and extract") boolean dryRun,
            @ShellOption(value = "--force", defaultValue = "false",
                         help = "Force re-execution for Phase 3 extraction") boolean force,
            @ShellOption(value = "--llm-threshold", defaultValue = ShellOption.NULL,
                         help = "Override LLM threshold for enrichment") Integer llmThreshold) {

        var sb = new StringBuilder();
        sb.append("=== Full Pipeline Run ===\n\n");

        sb.append(stripSuggestions(scanCommand.executeScan(manifestPath, resume))).append("\n");
        sb.append(stripSuggestions(planCommand.plan(manifestPath))).append("\n");
        sb.append(stripSuggestions(enrichCommand.enrich(manifestPath, dryRun, resume, llmThreshold))).append("\n");
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
