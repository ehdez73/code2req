package com.github.ehdez73.code2req.infrastructure.persistence;

import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.common.port.TaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class TaskStore implements TaskRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<Task> rowMapper = (rs, rowNum) -> new Task(
        rs.getString("task_id"),
        rs.getString("file_path"),
        TaskStatus.valueOf(rs.getString("status")),
        rs.getString("content_type"),
        rs.getString("content_hash"),
        rs.getString("target_name"),
        rs.getString("paired_test_path"),
        rs.getString("created_at"),
        rs.getString("updated_at")
    );

    public TaskStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Task task) {
        jdbc.update("""
            INSERT OR REPLACE INTO tasks (task_id, file_path, status, content_type, content_hash, target_name, paired_test_path, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, task.taskId(), task.filePath(), task.status().name(), task.contentType(), task.contentHash(),
            task.targetName(), task.pairedTestPath(),
            task.createdAt() != null ? task.createdAt() : LocalDateTime.now().toString(),
            task.updatedAt() != null ? task.updatedAt() : LocalDateTime.now().toString());
    }

    public Optional<Task> findById(String taskId) {
        List<Task> tasks = jdbc.query("SELECT * FROM tasks WHERE task_id = ?", rowMapper, taskId);
        return tasks.isEmpty() ? Optional.empty() : Optional.of(tasks.get(0));
    }

    public List<Task> findAll() {
        return jdbc.query("SELECT * FROM tasks ORDER BY created_at", rowMapper);
    }

    public List<Task> findByStatus(TaskStatus status) {
        return jdbc.query("SELECT * FROM tasks WHERE status = ? ORDER BY created_at", rowMapper, status.name());
    }

    public List<Task> findByStatusWithoutFinding(TaskStatus status, String findingType) {
        return jdbc.query("""
            SELECT t.* FROM tasks t
            LEFT JOIN execution_findings f
                ON f.task_id = t.task_id AND f.finding_type = ?
            WHERE t.status = ? AND f.id IS NULL
            ORDER BY t.created_at
        """, rowMapper, findingType, status.name());
    }

    public List<Task> findByStatusesWithoutFinding(List<TaskStatus> statuses, String findingType) {
        String placeholders = statuses.stream().map(s -> "?").collect(Collectors.joining(","));
        Object[] params = new Object[statuses.size() + 1];
        params[0] = findingType;
        for (int i = 0; i < statuses.size(); i++) {
            params[i + 1] = statuses.get(i).name();
        }
        return jdbc.query(
            "SELECT t.* FROM tasks t LEFT JOIN execution_findings f" +
            " ON f.task_id = t.task_id AND f.finding_type = ?" +
            " WHERE t.status IN (" + placeholders + ") AND f.id IS NULL" +
            " ORDER BY t.created_at",
            rowMapper, params);
    }

    public void updateStatus(String taskId, TaskStatus status) {
        jdbc.update("UPDATE tasks SET status = ?, updated_at = ? WHERE task_id = ?",
            status.name(), LocalDateTime.now().toString(), taskId);
    }

    public int updateStatusByOldStatus(TaskStatus oldStatus, TaskStatus newStatus) {
        return jdbc.update("UPDATE tasks SET status = ?, updated_at = ? WHERE status = ?",
            newStatus.name(), LocalDateTime.now().toString(), oldStatus.name());
    }

    public List<Task> findByTargetPrefix(String targetPrefix) {
        return jdbc.query("SELECT * FROM tasks WHERE target_name LIKE ? ORDER BY created_at",
            rowMapper, "%" + targetPrefix + "%");
    }

    public List<Task> findByStatusAndTargetPrefix(TaskStatus status, String targetPrefix) {
        return jdbc.query("SELECT * FROM tasks WHERE status = ? AND target_name LIKE ? ORDER BY created_at",
            rowMapper, status.name(), "%" + targetPrefix + "%");
    }

    public Task findByIdOrPrefix(String input) {
        Optional<Task> exact = findById(input);
        if (exact.isPresent()) return exact.get();
        List<Task> matches = findByPrefix(input);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No task found matching: " + input);
        }
        if (matches.size() > 1) {
            var sb = new StringBuilder("Multiple tasks match prefix '").append(input).append("':\n");
            for (Task t : matches) {
                sb.append("  ").append(t.taskId()).append("  ").append(t.filePath()).append('\n');
            }
            throw new IllegalArgumentException(sb.toString());
        }
        return matches.get(0);
    }

    @Override
    public List<Task> findByPrefix(String input) {
        return jdbc.query("SELECT * FROM tasks WHERE task_id LIKE ? ORDER BY created_at",
            rowMapper, input + "%");
    }

    public Optional<Task> findByFilePath(String filePath) {
        List<Task> tasks = jdbc.query("SELECT * FROM tasks WHERE file_path = ? ORDER BY created_at DESC LIMIT 1",
            rowMapper, filePath);
        return tasks.isEmpty() ? Optional.empty() : Optional.of(tasks.get(0));
    }

    public void deleteAll() {
        jdbc.execute("DELETE FROM tasks");
    }

    public int deleteByStatus(TaskStatus status) {
        return jdbc.update("DELETE FROM tasks WHERE status = ?", status.name());
    }

    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Integer.class);
        return count != null ? count : 0;
    }

    public int countByStatus(TaskStatus status) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE status = ?", Integer.class, status.name());
        return count != null ? count : 0;
    }
}
