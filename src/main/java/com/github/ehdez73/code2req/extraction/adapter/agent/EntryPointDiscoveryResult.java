package com.github.ehdez73.code2req.extraction.adapter.agent;

import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.OrphanedMethod;

import java.util.List;

public record EntryPointDiscoveryResult(
    List<EntryPoint> entryPoints,
    List<OrphanedMethod> orphanedMethods
) {}
