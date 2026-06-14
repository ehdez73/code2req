package com.github.ehdez73.code2req.output;

import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.store.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class OrphanRecovery {
    private static final Logger log = LoggerFactory.getLogger(OrphanRecovery.class);
    private final TaskStore taskStore;

    public OrphanRecovery(TaskStore taskStore) {
        this.taskStore = taskStore;
    }

    public OrphanRecoveryResult recover() {
        List<Task> orphans = taskStore.findByStatus(TaskStatus.RUNNING);
        int reverted = 0;

        for (Task task : orphans) {
            taskStore.updateStatus(task.taskId(), TaskStatus.PENDING);
            reverted++;
        }

        if (reverted > 0) {
            log.info("Orphan recovery: {} task(s) reverted to PENDING", reverted);
        } else {
            log.debug("Orphan recovery: no orphaned tasks found");
        }

        return new OrphanRecoveryResult(orphans.size(), reverted);
    }
}
