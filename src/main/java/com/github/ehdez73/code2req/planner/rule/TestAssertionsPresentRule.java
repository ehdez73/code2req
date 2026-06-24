package com.github.ehdez73.code2req.planner.rule;

import com.github.ehdez73.code2req.executor.testmining.TestFileMatcher;
import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.planner.PlanningContext;
import com.github.ehdez73.code2req.planner.QualificationRule;
import org.springframework.stereotype.Component;

/**
 * Qualifies a task when the production file has a paired test file
 * (e.g. {@code OrderService.java} ↔ {@code OrderServiceTest.java}).
 * Test assertions are mined and fed to the executor alongside the
 * production source for concurrent semantic enrichment.
 */
@Component
public class TestAssertionsPresentRule implements QualificationRule {

    private final TestFileMatcher testFileMatcher;

    public TestAssertionsPresentRule(TestFileMatcher testFileMatcher) {
        this.testFileMatcher = testFileMatcher;
    }

    @Override
    public QualificationReason reason() {
        return QualificationReason.TEST_ASSERTIONS_PRESENT;
    }

    @Override
    public boolean evaluate(Task task, PlanningContext ctx) {
        return testFileMatcher.findTestFilePath(task.filePath()).isPresent();
    }
}
