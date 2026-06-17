package com.github.ehdez73.code2req.store;

import com.github.ehdez73.code2req.analyzer.event.link.TopicLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TopicLinkStore {

    private static final Logger log = LoggerFactory.getLogger(TopicLinkStore.class);

    private final JdbcTemplate jdbc;
    private final RowMapper<TopicLink> rowMapper = (rs, rowNum) -> new TopicLink(
        rs.getString("broker"),
        rs.getString("topic_or_queue"),
        null, null, null, null,
        rs.getString("resolved_status")
    );

    public TopicLinkStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveAll(List<TopicLink> links) {
        for (TopicLink link : links) {
            jdbc.update("""
                INSERT INTO topic_links (broker, topic_or_queue, producer_task_id, consumer_task_id, resolved_status, confidence)
                VALUES (?, ?, ?, ?, ?, ?)
            """, link.brokerType(), link.topic(), link.producerFilePath(), link.consumerFilePath(),
                link.resolvedStatus(), 1.0);
        }
        if (!links.isEmpty()) {
            log.info("Persisted {} topic link(s)", links.size());
        }
    }

    public List<TopicLink> findByBroker(String broker) {
        return jdbc.query("SELECT * FROM topic_links WHERE broker = ?", rowMapper, broker);
    }

    public List<TopicLink> findByStatus(String status) {
        return jdbc.query("SELECT * FROM topic_links WHERE resolved_status = ?", rowMapper, status);
    }

    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM topic_links", Integer.class);
        return count != null ? count : 0;
    }

    public void deleteAll() {
        jdbc.execute("DELETE FROM topic_links");
    }
}