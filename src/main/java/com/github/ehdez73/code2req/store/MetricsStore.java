package com.github.ehdez73.code2req.store;

import com.github.ehdez73.code2req.model.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MetricsStore {

    private static final Logger log = LoggerFactory.getLogger(MetricsStore.class);

    private final JdbcTemplate jdbc;
    private final RowMapper<Metric> rowMapper = (rs, rowNum) -> new Metric(
        rs.getString("run_id"),
        rs.getInt("phase"),
        rs.getInt("tasks_total"),
        rs.getInt("tasks_completed"),
        rs.getInt("edges_resolved"),
        rs.getInt("edges_unresolved"),
        rs.getInt("topic_links_resolved"),
        rs.getInt("floating_links_registered"),
        rs.getInt("tokens_consumed"),
        rs.getDouble("api_cost_estimated"),
        rs.getString("recorded_at")
    );

    public MetricsStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Metric metric) {
        jdbc.update("""
            INSERT INTO metrics (run_id, phase, tasks_total, tasks_completed,
                                 edges_resolved, edges_unresolved,
                                 topic_links_resolved, floating_links_registered,
                                 tokens_consumed, api_cost_estimated, recorded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, metric.runId(), metric.phase(), metric.tasksTotal(), metric.tasksCompleted(),
            metric.edgesResolved(), metric.edgesUnresolved(),
            metric.topicLinksResolved(), metric.floatingLinksRegistered(),
            metric.tokensConsumed(), metric.apiCostEstimated(), metric.recordedAt());
        log.info("Metrics persisted for run {} phase {}", metric.runId(), metric.phase());
    }

    public Metric getLatestForPhase(int phase) {
        List<Metric> results = jdbc.query(
            "SELECT * FROM metrics WHERE phase = ? ORDER BY recorded_at DESC LIMIT 1",
            rowMapper, phase);
        return results.isEmpty() ? null : results.get(0);
    }

    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM metrics", Integer.class);
        return count != null ? count : 0;
    }

    public void deleteAll() {
        jdbc.execute("DELETE FROM metrics");
    }
}