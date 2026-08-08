package com.github.ehdez73.code2req.infrastructure.cli.command;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ehdez73.code2req.extraction.ExtractionCache;
import com.github.ehdez73.code2req.extraction.ExtractionOrchestrator;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPointType;
import com.github.ehdez73.code2req.extraction.domain.model.FlowSummary;
import com.github.ehdez73.code2req.extraction.domain.model.FlowSummaryAssembler;
import com.github.ehdez73.code2req.extraction.domain.model.FlowSummaryStatus;
import com.github.ehdez73.code2req.extraction.domain.model.StructuralGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@ShellComponent
public class FlowCommand {

    private static final Logger log = LoggerFactory.getLogger(FlowCommand.class);

    private final ExtractionOrchestrator extractionOrchestrator;
    private final Path cacheFilePath;
    private final ObjectMapper objectMapper;

    public FlowCommand(ExtractionOrchestrator extractionOrchestrator,
                       @Value("${code2req.output.spec-dir}") String specDir,
                       @Value("${code2req.output.extraction-cache-file}") String cacheFile) {
        this.extractionOrchestrator = extractionOrchestrator;
        this.cacheFilePath = Path.of(specDir, cacheFile).normalize();
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @ShellMethod(key = "flow list", value = "List all detected execution flows with status")
    public String list(
            @ShellOption(value = "--status", defaultValue = ShellOption.NULL,
                          help = "Filter by status: analyzed, quarantined, pending") String statusFilter,
            @ShellOption(value = "--type", defaultValue = ShellOption.NULL,
                          help = "Filter by type: http, scheduled, kafka, rabbitmq, activemq, event") String typeFilter,
            @ShellOption(value = "--filter", defaultValue = ShellOption.NULL,
                          help = "Filter by partial match on short ID or name") String textFilter,
            @ShellOption(value = "--verbose", defaultValue = "false",
                          help = "Show additional details: step count, complexity, unresolved links, stale indicator") boolean verbose) {

        StructuralGraph graph = extractionOrchestrator.buildCodebaseKnowledge().structuralGraph();
        ExtractionCache cache = loadCache();

        if (graph.getEntryPoints().isEmpty() && cache == null) {
            return "No data. Run 'scan' first.";
        }
        FlowSummaryAssembler assembler = new FlowSummaryAssembler(graph, cache);
        List<FlowSummary> allFlows = assembler.assemble();

        if (statusFilter != null) {
            String filter = statusFilter.toLowerCase();
            allFlows = allFlows.stream()
                .filter(f -> f.status().name().equalsIgnoreCase(filter))
                .toList();
        }

        if (typeFilter != null) {
            EntryPointType type = parseType(typeFilter);
            if (type == null) {
                return "Error: Invalid type '" + typeFilter
                    + "'. Valid values: http, scheduled, kafka, rabbitmq, activemq, event";
            }
            EntryPointType finalType = type;
            allFlows = allFlows.stream()
                .filter(f -> f.type() == finalType)
                .toList();
        }

        if (textFilter != null) {
            String lower = textFilter.toLowerCase();
            allFlows = allFlows.stream()
                .filter(f -> f.shortId().toLowerCase().contains(lower)
                    || f.name().toLowerCase().contains(lower))
                .toList();
        }

        if (allFlows.isEmpty()) {
            return "No flows match the specified filters.";
        }

        var sb = new StringBuilder();
        sb.append(String.format("%-10s %-12s %-14s %s%n",
            "SHORT ID", "TYPE", "STATUS", "NAME"));
        sb.append("-".repeat(80)).append("\n");

        for (FlowSummary flow : allFlows) {
            String statusDisplay = flow.status().name();
            if (verbose && flow.hasStaleLinks()) {
                statusDisplay += " [stale links]";
            }
            if (verbose) {
                sb.append(String.format("%-10s %-12s %-14s %s%n",
                    flow.shortId(), flow.type(), statusDisplay, flow.name()));
                sb.append(String.format("          steps=%-4d complexity=%-10s unresolved=%d%n",
                    flow.stepCount(),
                    flow.complexity() != null ? flow.complexity().name() : "-",
                    flow.unresolvedLinkCount()));
            } else {
                sb.append(String.format("%-10s %-12s %-14s %s%n",
                    flow.shortId(), flow.type(), statusDisplay, flow.name()));
            }
        }

        long analyzed = allFlows.stream().filter(f -> f.status() == FlowSummaryStatus.ANALYZED).count();
        long quarantined = allFlows.stream().filter(f -> f.status() == FlowSummaryStatus.QUARANTINED).count();
        long pending = allFlows.stream().filter(f -> f.status() == FlowSummaryStatus.PENDING).count();
        sb.append("\n").append(String.format("%d flows: %d analyzed, %d quarantined, %d pending",
            allFlows.size(), analyzed, quarantined, pending));

        return sb.toString();
    }

    private ExtractionCache loadCache() {
        if (!Files.exists(cacheFilePath)) {
            return null;
        }
        try {
            return objectMapper.readValue(cacheFilePath.toFile(), ExtractionCache.class);
        } catch (IOException e) {
            log.debug("Could not read extraction cache at {}: {}", cacheFilePath, e.getMessage());
            return null;
        }
    }

    private static EntryPointType parseType(String type) {
        return switch (type.toLowerCase()) {
            case "http" -> EntryPointType.HTTP;
            case "scheduled" -> EntryPointType.SCHEDULED;
            case "kafka" -> EntryPointType.KAFKA;
            case "rabbitmq" -> EntryPointType.RABBITMQ;
            case "activemq" -> EntryPointType.ACTIVEMQ;
            case "event" -> EntryPointType.EVENT_LISTENER;
            default -> null;
        };
    }
}
