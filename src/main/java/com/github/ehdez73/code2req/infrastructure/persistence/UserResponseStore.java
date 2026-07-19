package com.github.ehdez73.code2req.infrastructure.persistence;

import com.github.ehdez73.code2req.extraction.domain.model.UserResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserResponseStore {

    private final JdbcTemplate jdbc;

    public UserResponseStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(UserResponse response) {
        jdbc.update("""
            INSERT INTO user_responses (session_id, question, answer)
            VALUES (?, ?, ?)
        """, response.sessionId(), response.question(), response.answer());
    }

    public List<UserResponse> findBySessionId(String sessionId) {
        return jdbc.query("SELECT session_id, question, answer, created_at FROM user_responses WHERE session_id = ?",
            (rs, row) -> new UserResponse(
                rs.getString("session_id"),
                rs.getString("question"),
                rs.getString("answer"),
                rs.getString("created_at")
            ), sessionId);
    }

    public Optional<UserResponse> findByQuestion(String question) {
        List<UserResponse> results = jdbc.query("""
            SELECT session_id, question, answer, created_at FROM user_responses
            WHERE question = ? ORDER BY id DESC LIMIT 1
        """, (rs, row) -> new UserResponse(
                rs.getString("session_id"),
                rs.getString("question"),
                rs.getString("answer"),
                rs.getString("created_at")
        ), question);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
