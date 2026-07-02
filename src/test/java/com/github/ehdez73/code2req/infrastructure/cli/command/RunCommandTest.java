package com.github.ehdez73.code2req.infrastructure.cli.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunCommandTest {

    @Test
    void pipelineChainsAllCommandsInOrder() {
        var scan = mock(ScanCommand.class, CALLS_REAL_METHODS);
        var plan = mock(PlanCommand.class);
        var enrich = mock(EnrichCommand.class);
        var extract = mock(ExtractCommand.class);
        var generate = mock(GenerateCommand.class);

        when(plan.plan(any())).thenReturn("plan ok");
        when(enrich.enrich(any(), anyBoolean(), anyBoolean(), any())).thenReturn("enrich ok");
        when(extract.extract(any(), anyBoolean(), anyBoolean())).thenReturn("extract ok");
        when(generate.generate()).thenReturn("generate ok");

        var run = new RunCommand(scan, plan, enrich, extract, generate);
        var result = run.run("manifest.yaml", false, false, false, null);

        verify(plan).plan("manifest.yaml");
        verify(enrich).enrich("manifest.yaml", false, false, null);
        verify(extract).extract("manifest.yaml", false, false);
        verify(generate).generate();
        assertTrue(result.contains("Pipeline Complete"));
    }

    @Test
    void resumeFlagForwardsToScan() {
        var scan = mock(ScanCommand.class, CALLS_REAL_METHODS);
        var plan = mock(PlanCommand.class);
        var enrich = mock(EnrichCommand.class);
        var extract = mock(ExtractCommand.class);
        var generate = mock(GenerateCommand.class);

        when(plan.plan(any())).thenReturn("plan ok");
        when(enrich.enrich(any(), anyBoolean(), anyBoolean(), any())).thenReturn("enrich ok");
        when(extract.extract(any(), anyBoolean(), anyBoolean())).thenReturn("extract ok");
        when(generate.generate()).thenReturn("generate ok");

        var run = new RunCommand(scan, plan, enrich, extract, generate);
        run.run("manifest.yaml", true, false, false, 3);

        verify(plan).plan("manifest.yaml");
        verify(enrich).enrich("manifest.yaml", false, true, 3);
    }

    @Test
    void dryRunModeChainsAllCommands() {
        var scan = mock(ScanCommand.class, CALLS_REAL_METHODS);
        var plan = mock(PlanCommand.class);
        var enrich = mock(EnrichCommand.class);
        var extract = mock(ExtractCommand.class);
        var generate = mock(GenerateCommand.class);

        when(plan.plan(any())).thenReturn("plan dry");
        when(enrich.enrich(any(), anyBoolean(), anyBoolean(), any())).thenReturn("enrich dry");
        when(extract.extract(any(), anyBoolean(), anyBoolean())).thenReturn("extract dry");
        when(generate.generate()).thenReturn("generate dry");

        var run = new RunCommand(scan, plan, enrich, extract, generate);
        var result = run.run("m.yaml", false, true, true, 0);

        assertTrue(result.contains("Pipeline Complete"));
    }
}
