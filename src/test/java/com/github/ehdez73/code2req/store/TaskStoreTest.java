package com.github.ehdez73.code2req.store;

import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/test-store-" + "${random.uuid}" + ".db"
})
class TaskStoreTest {

    @Autowired
    private TaskStore taskStore;

    @Autowired
    private TaskStoreSchema taskStoreSchema;

    @BeforeEach
    void setUp() {
        taskStoreSchema.dropTable();
        taskStoreSchema.createSchemaIfNotExists();
    }

    @AfterEach
    void tearDown() {
        taskStoreSchema.dropTable();
    }

    @Test
    void saveAndFindById() {
        Task task = new Task("id-1", "src/main/App.java", TaskStatus.PENDING, "java", "hash123");
        taskStore.save(task);

        Optional<Task> found = taskStore.findById("id-1");
        assertTrue(found.isPresent());
        assertEquals("id-1", found.get().taskId());
        assertEquals("src/main/App.java", found.get().filePath());
        assertEquals(TaskStatus.PENDING, found.get().status());
        assertEquals("java", found.get().contentType());
        assertEquals("hash123", found.get().contentHash());
    }

    @Test
    void saveReplacesExisting() {
        Task task = new Task("id-1", "src/main/App.java", TaskStatus.PENDING, "java", "hash123");
        taskStore.save(task);

        Task updated = new Task("id-1", "src/main/App.java", TaskStatus.SUCCESS, "java", "hash123");
        taskStore.save(updated);

        Optional<Task> found = taskStore.findById("id-1");
        assertTrue(found.isPresent());
        assertEquals(TaskStatus.SUCCESS, found.get().status());
    }

    @Test
    void findByIdReturnsEmptyForMissing() {
        Optional<Task> found = taskStore.findById("nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void findAll() {
        taskStore.save(new Task("id-1", "file1.java", TaskStatus.PENDING, "java", "h1"));
        taskStore.save(new Task("id-2", "file2.java", TaskStatus.PENDING, "java", "h2"));
        taskStore.save(new Task("id-3", "file3.java", TaskStatus.PENDING, "java", "h3"));

        List<Task> all = taskStore.findAll();
        assertEquals(3, all.size());
    }

    @Test
    void findByStatus() {
        taskStore.save(new Task("id-1", "file1.java", TaskStatus.PENDING, "java", "h1"));
        taskStore.save(new Task("id-2", "file2.java", TaskStatus.RUNNING, "java", "h2"));
        taskStore.save(new Task("id-3", "file3.java", TaskStatus.SUCCESS, "java", "h3"));

        assertEquals(1, taskStore.findByStatus(TaskStatus.PENDING).size());
        assertEquals(1, taskStore.findByStatus(TaskStatus.RUNNING).size());
        assertEquals(1, taskStore.findByStatus(TaskStatus.SUCCESS).size());
    }

    @Test
    void updateStatus() {
        taskStore.save(new Task("id-1", "file1.java", TaskStatus.PENDING, "java", "h1"));
        taskStore.updateStatus("id-1", TaskStatus.RUNNING);

        Optional<Task> found = taskStore.findById("id-1");
        assertTrue(found.isPresent());
        assertEquals(TaskStatus.RUNNING, found.get().status());
    }

    @Test
    void deleteAll() {
        taskStore.save(new Task("id-1", "file1.java", TaskStatus.PENDING, "java", "h1"));
        taskStore.save(new Task("id-2", "file2.java", TaskStatus.PENDING, "java", "h2"));

        assertEquals(2, taskStore.count());
        taskStore.deleteAll();
        assertEquals(0, taskStore.count());
    }

    @Test
    void count() {
        assertEquals(0, taskStore.count());
        taskStore.save(new Task("id-1", "file1.java", TaskStatus.PENDING, "java", "h1"));
        assertEquals(1, taskStore.count());
    }
}
