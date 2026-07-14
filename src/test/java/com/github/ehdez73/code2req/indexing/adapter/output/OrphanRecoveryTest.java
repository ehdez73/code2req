package com.github.ehdez73.code2req.indexing.adapter.output;

import com.embabel.agent.core.AgentPlatform;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FloatingLinkStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStoreSchema;
import com.github.ehdez73.code2req.enrichment.domain.model.OrphanRecoveryResult;
import com.github.ehdez73.code2req.enrichment.domain.service.OrphanRecovery;
import com.github.ehdez73.code2req.infrastructure.persistence.TopicLinkStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.github.ehdez73.code2req.Application;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = Application.class, properties = {
    "spring.datasource.url=jdbc:sqlite:target/test-orphan-" + "${random.uuid}" + ".db"
})
class OrphanRecoveryTest {

    @MockitoBean
    private AgentPlatform agentPlatform;

    @Autowired
    private TaskStore taskStore;

    @Autowired
    private TaskStoreSchema taskStoreSchema;

    @Autowired
    private ExecutionFindingStore executionFindingStore;

    @Autowired
    private TopicLinkStore topicLinkStore;

    @Autowired
    private FloatingLinkStore floatingLinkStore;

    private OrphanRecovery recovery;

    @BeforeEach
    void setUp() {
        taskStoreSchema.dropTable();
        taskStoreSchema.createSchemaIfNotExists();
        recovery = new OrphanRecovery(taskStore, executionFindingStore, topicLinkStore, floatingLinkStore);
    }

    @AfterEach
    void tearDown() {
        taskStoreSchema.dropTable();
    }

    @Test
    void noOrphansReturnsZeroCounts() {
        taskStore.save(new Task("id-1", "file1.java", TaskStatus.PENDING, "java", "h1", "test"));
        taskStore.save(new Task("id-2", "file2.java", TaskStatus.INDEXED, "java", "h2", "test"));

        OrphanRecoveryResult result = recovery.recover();

        assertEquals(0, result.orphanedCount());
        assertEquals(0, result.revertedCount());
        assertFalse(result.recovered());
    }

    @Test
    void revertsRunningTasksToEnrichPending() {
        taskStore.save(new Task("id-1", "file1.java", TaskStatus.ENRICHING, "java", "h1", "test"));
        taskStore.save(new Task("id-2", "file2.java", TaskStatus.ENRICHING, "java", "h2", "test"));

        OrphanRecoveryResult result = recovery.recover();

        assertEquals(2, result.orphanedCount());
        assertEquals(2, result.revertedCount());
        assertTrue(result.recovered());

        assertEquals(TaskStatus.ENRICH_PENDING, taskStore.findById("id-1").get().status());
        assertEquals(TaskStatus.ENRICH_PENDING, taskStore.findById("id-2").get().status());
    }

    @Test
    void onlyRunningTasksAreReverted() {
        taskStore.save(new Task("id-1", "file1.java", TaskStatus.ENRICHING, "java", "h1", "test"));
        taskStore.save(new Task("id-2", "file2.java", TaskStatus.INDEXED, "java", "h2", "test"));
        taskStore.save(new Task("id-3", "file3.java", TaskStatus.PENDING, "java", "h3", "test"));
        taskStore.save(new Task("id-4", "file4.java", TaskStatus.FAILED, "java", "h4", "test"));

        OrphanRecoveryResult result = recovery.recover();

        assertEquals(1, result.orphanedCount());
        assertEquals(1, result.revertedCount());

        assertEquals(TaskStatus.ENRICH_PENDING, taskStore.findById("id-1").get().status());
        assertEquals(TaskStatus.INDEXED, taskStore.findById("id-2").get().status());
        assertEquals(TaskStatus.PENDING, taskStore.findById("id-3").get().status());
        assertEquals(TaskStatus.FAILED, taskStore.findById("id-4").get().status());
    }

    @Test
    void mixedRunningAndNonRunningCountsCorrect() {
        taskStore.save(new Task("id-1", "f1.java", TaskStatus.ENRICHING, "java", "h1", "test"));
        taskStore.save(new Task("id-2", "f2.java", TaskStatus.ENRICHING, "java", "h2", "test"));
        taskStore.save(new Task("id-3", "f3.java", TaskStatus.INDEXED, "java", "h3", "test"));

        OrphanRecoveryResult result = recovery.recover();

        assertEquals(2, result.orphanedCount());
        assertEquals(2, result.revertedCount());
    }
}
