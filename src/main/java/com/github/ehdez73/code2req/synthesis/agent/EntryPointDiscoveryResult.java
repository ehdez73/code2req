package com.github.ehdez73.code2req.synthesis.agent;

import com.github.ehdez73.code2req.synthesis.domain.EntryPoint;
import com.github.ehdez73.code2req.synthesis.domain.OrphanedMethod;

import java.util.List;

public record EntryPointDiscoveryResult(
    List<EntryPoint> entryPoints,
    List<OrphanedMethod> orphanedMethods
) {}
