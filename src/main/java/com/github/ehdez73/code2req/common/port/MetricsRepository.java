package com.github.ehdez73.code2req.common.port;

import com.github.ehdez73.code2req.common.domain.Metric;

import java.util.Optional;

public interface MetricsRepository {
    void save(Metric metric);

    Optional<Metric> getLatestForPhase(String phase);

    Metric getLatestForPhase(int phase);

    int count();

    void deleteAll();
}
