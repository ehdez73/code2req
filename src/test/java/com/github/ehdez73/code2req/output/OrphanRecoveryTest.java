package com.github.ehdez73.code2req.output;

import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.ehdez73.code2req.store.TaskStoreSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/test-orphan-" + "${random.uuid}" + ".db"
})
class OrphanRecoveryTest {

    @Autowired
    private TaskStore taskStore;

    @Autowired
    private TaskStoreSchema taskStoreSchema;

    private OrphanRecovery recovery;

    @BeforeEach
    void setUp() {
        taskStoreSchema.dropTable();
        taskStoreSchema.createSchemaIfNotExists();
        recovery = new OrphanRecovery(taskStore);
    }

    @AfterEach
    void tearDown() {
        taskStoreSchema.dropTable();
    }

    @Test
    void noOrphansReturnsZeroCounts() {
        taskStore.save(new Task("id-1", "file1.java", TaskStatus.PENDING, "java", "h1"));
        taskStore.save(new Task("id-2", "file2.java", TaskStatus.SUCCESS, "java", "h2"));

        OrphanRecoveryResult result = recovery.recover();

        assertEquals(0, result.orphanedCount());
        assertEquals(0, result.revertedCount());
        assertFalse(result.recovered());
    }

    @Test
    void revertsRunningTasksToPending() {
        taskStore.save(new Task("id-1", "file1.java", TaskStatus.RUNNING, "java", "h1"));
        taskStore.save(new Task("id-2", "file2.java", TaskStatus.RUNNING, "java", "h2"));

        OrphanRecoveryResult result = recovery.recover();

        assertEquals(2, result.orphanedCount());
        assertEquals(2, result.revertedCount());
        assertTrue(result.recovered());

        assertEquals(TaskStatus.PENDING, taskStore.findById("id-1").get().status());
        assertEquals(TaskStatus.PENDING, taskStore.findById("id-2").get().status());
    }

    @Test
    void onlyRunningTasksAreReverted() {
        taskStore.save(new Task("id-1", "file1.java", TaskStatus.RUNNING, "java", "h1"));
        taskStore.save(new Task("id-2", "file2.java", TaskStatus.SUCCESS, "java", "h2"));
        taskStore.save(new Task("id-3", "file3.java", TaskStatus.PENDING, "java", "h3"));
        taskStore.save(new Task("id-4", "file4.java", TaskStatus.FAILED, "java", "h4"));

        OrphanRecoveryResult result = recovery.recover();

        assertEquals(1, result.orphanedCount());
        assertEquals(1, result.revertedCount());

        assertEquals(TaskStatus.PENDING, taskStore.findById("id-1").get().status());
        assertEquals(TaskStatus.SUCCESS, taskStore.findById("id-2").get().status());
        assertEquals(TaskStatus.PENDING, taskStore.findById("id-3").get().status());
        assertEquals(TaskStatus.FAILED, taskStore.findById("id-4").get().status());
    }

    @Test
    void mixedRunningAndNonRunningCountsCorrect() {
        taskStore.save(new Task("id-1", "f1.java", TaskStatus.RUNNING, "java", "h1"));
        taskStore.save(new Task("id-2", "f2.java", TaskStatus.RUNNING, "java", "h2"));
        taskStore.save(new Task("id-3", "f3.java", TaskStatus.SUCCESS, "java", "h3"));

        OrphanRecoveryResult result = recovery.recover();

        assertEquals(2, result.orphanedCount());
        assertEquals(2, result.revertedCount());
    }
}
