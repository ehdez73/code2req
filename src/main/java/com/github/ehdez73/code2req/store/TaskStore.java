package com.github.ehdez73.code2req.store;

import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class TaskStore {
    private final JdbcTemplate jdbc;
    private final RowMapper<Task> rowMapper = (rs, rowNum) -> new Task(
        rs.getString("task_id"),
        rs.getString("file_path"),
        TaskStatus.valueOf(rs.getString("status")),
        rs.getString("content_type"),
        rs.getString("content_hash"),
        rs.getString("created_at"),
        rs.getString("updated_at")
    );

    public TaskStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Task task) {
        jdbc.update("""
            INSERT OR REPLACE INTO tasks (task_id, file_path, status, content_type, content_hash, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, task.taskId(), task.filePath(), task.status().name(), task.contentType(), task.contentHash(),
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

    public void updateStatus(String taskId, TaskStatus status) {
        jdbc.update("UPDATE tasks SET status = ?, updated_at = ? WHERE task_id = ?",
            status.name(), LocalDateTime.now().toString(), taskId);
    }

    public void deleteAll() {
        jdbc.execute("DELETE FROM tasks");
    }

    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Integer.class);
        return count != null ? count : 0;
    }
}
