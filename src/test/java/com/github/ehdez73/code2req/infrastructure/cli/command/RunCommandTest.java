package com.github.ehdez73.code2req.infrastructure.cli.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunCommandTest {

    @Test
    void pipelineChainsAllCommandsInOrder() {
        var scan = mock(ScanCommand.class, CALLS_REAL_METHODS);
        var enrich = mock(EnrichCommand.class);
        var extract = mock(ExtractCommand.class);
        var generate = mock(GenerateCommand.class);

        when(enrich.enrich(any(), anyBoolean(), anyBoolean(), anyBoolean(), any())).thenReturn("enrich ok");
        when(extract.extract(any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn("extract ok");
        when(generate.generate()).thenReturn("generate ok");

        var run = new RunCommand(scan, enrich, extract, generate);
        var result = run.run("manifest.yaml", false, false, false, null, false);

        verify(enrich).enrich(eq("manifest.yaml"), eq(false), eq(false), eq(false), isNull());
        verify(extract).extract("manifest.yaml", false, false, false, false);
        verify(generate).generate();
        assertTrue(result.contains("Pipeline Complete"));
    }

    @Test
    void resumeFlagForwardsToScan() {
        var scan = mock(ScanCommand.class, CALLS_REAL_METHODS);
        var enrich = mock(EnrichCommand.class);
        var extract = mock(ExtractCommand.class);
        var generate = mock(GenerateCommand.class);

        when(enrich.enrich(any(), anyBoolean(), anyBoolean(), anyBoolean(), any())).thenReturn("enrich ok");
        when(extract.extract(any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn("extract ok");
        when(generate.generate()).thenReturn("generate ok");

        var run = new RunCommand(scan, enrich, extract, generate);
        run.run("manifest.yaml", true, false, false, 3, false);

        verify(enrich).enrich(eq("manifest.yaml"), eq(false), eq(false), eq(true), eq(3));
    }

    @Test
    void dryRunModeChainsAllCommands() {
        var scan = mock(ScanCommand.class, CALLS_REAL_METHODS);
        var enrich = mock(EnrichCommand.class);
        var extract = mock(ExtractCommand.class);
        var generate = mock(GenerateCommand.class);

        when(enrich.enrich(any(), anyBoolean(), anyBoolean(), anyBoolean(), any())).thenReturn("enrich dry");
        when(extract.extract(any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn("extract dry");
        when(generate.generate()).thenReturn("generate dry");

        var run = new RunCommand(scan, enrich, extract, generate);
        var result = run.run("m.yaml", false, true, true, 0, false);

        assertTrue(result.contains("Pipeline Complete"));
    }

    @Test
    void headlessFlagForwardsToExtract() {
        var scan = mock(ScanCommand.class, CALLS_REAL_METHODS);
        var enrich = mock(EnrichCommand.class);
        var extract = mock(ExtractCommand.class);
        var generate = mock(GenerateCommand.class);

        when(enrich.enrich(any(), anyBoolean(), anyBoolean(), anyBoolean(), any())).thenReturn("enrich ok");
        when(extract.extract(any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn("extract ok");
        when(generate.generate()).thenReturn("generate ok");

        var run = new RunCommand(scan, enrich, extract, generate);
        run.run("m.yaml", false, false, false, null, true);

        verify(extract).extract("m.yaml", false, false, false, true);
    }
}
