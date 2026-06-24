package com.github.ehdez73.code2req.output;

import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.store.ExecutionFindingStore;
import com.github.ehdez73.code2req.store.FloatingLinkStore;
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.ehdez73.code2req.store.TopicLinkStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class OrphanRecovery {
    private static final Logger log = LoggerFactory.getLogger(OrphanRecovery.class);
    private final TaskStore taskStore;
    private final ExecutionFindingStore executionFindingStore;
    private final TopicLinkStore topicLinkStore;
    private final FloatingLinkStore floatingLinkStore;

    public OrphanRecovery(TaskStore taskStore,
                          ExecutionFindingStore executionFindingStore,
                          TopicLinkStore topicLinkStore,
                          FloatingLinkStore floatingLinkStore) {
        this.taskStore = taskStore;
        this.executionFindingStore = executionFindingStore;
        this.topicLinkStore = topicLinkStore;
        this.floatingLinkStore = floatingLinkStore;
    }

    public OrphanRecoveryResult recover() {
        List<Task> orphans = taskStore.findByStatus(TaskStatus.ENRICHING);
        int reverted = 0;

        for (Task task : orphans) {
            executionFindingStore.deleteByTaskId(task.taskId());
            topicLinkStore.deleteByTaskId(task.taskId());
            floatingLinkStore.deleteByTaskId(task.taskId());
            taskStore.updateStatus(task.taskId(), TaskStatus.ENRICH_PENDING);
            reverted++;
        }

        if (reverted > 0) {
            log.info("Orphan recovery: {} task(s) reverted from ENRICHING to ENRICH_PENDING, findings cleaned", reverted);
        } else {
            log.debug("Orphan recovery: no orphaned tasks found");
        }

        return new OrphanRecoveryResult(orphans.size(), reverted);
    }
}
