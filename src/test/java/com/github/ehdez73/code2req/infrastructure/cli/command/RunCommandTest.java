package com.github.ehdez73.code2req.infrastructure.cli.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunCommandTest {

    @Test
    void pipelineChainsAllCommandsInOrder() {
        var scan = mock(ScanCommand.class, CALLS_REAL_METHODS);
        var extract = mock(ExtractCommand.class);
        var generate = mock(GenerateCommand.class);

        when(extract.extract(any(), anyBoolean(), anyBoolean(), anyBoolean(), any(), anyBoolean())).thenReturn("extract ok");
        when(generate.generate()).thenReturn("generate ok");

        var run = new RunCommand(scan, extract, generate);
        var result = run.run("manifest.yaml", false, false, false);

        verify(extract).extract("manifest.yaml", false, false, false, null, false);
        verify(generate).generate();
        assertTrue(result.contains("Pipeline Complete"));
    }

    @Test
    void resumeFlagForwardsToScan() {
        var scan = mock(ScanCommand.class, CALLS_REAL_METHODS);
        var extract = mock(ExtractCommand.class);
        var generate = mock(GenerateCommand.class);

        when(extract.extract(any(), anyBoolean(), anyBoolean(), anyBoolean(), any(), anyBoolean())).thenReturn("extract ok");
        when(generate.generate()).thenReturn("generate ok");

        var run = new RunCommand(scan, extract, generate);
        run.run("manifest.yaml", true, false, false);

        verify(extract).extract("manifest.yaml", false, false, true, null, false);
    }

    @Test
    void dryRunModeChainsAllCommands() {
        var scan = mock(ScanCommand.class, CALLS_REAL_METHODS);
        var extract = mock(ExtractCommand.class);
        var generate = mock(GenerateCommand.class);

        when(extract.extract(any(), anyBoolean(), anyBoolean(), anyBoolean(), any(), anyBoolean())).thenReturn("extract dry");
        when(generate.generate()).thenReturn("generate dry");

        var run = new RunCommand(scan, extract, generate);
        var result = run.run("m.yaml", false, true, true);

        assertTrue(result.contains("Pipeline Complete"));
    }
}
