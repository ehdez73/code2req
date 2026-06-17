package com.github.ehdez73.code2req.planner.rule;

import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.planner.PlanningContext;
import com.github.ehdez73.code2req.planner.QualificationRule;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class TestAssertionsPresentRule implements QualificationRule {
    @Override
    public QualificationReason reason() {
        return QualificationReason.TEST_ASSERTIONS_PRESENT;
    }

    @Override
    public boolean evaluate(Task task, PlanningContext ctx) {
        return hasPairedTestFile(task.filePath());
    }

    public static boolean hasPairedTestFile(String sourceFilePath) {
        Path path = Paths.get(sourceFilePath);
        String fileName = path.getFileName().toString();
        if (!fileName.endsWith(".java")) {
            return false;
        }
        String baseName = fileName.substring(0, fileName.length() - ".java".length());
        Path parent = path.getParent();
        if (parent == null) {
            return false;
        }

        String[] testSuffixes = {"Test", "IT"};
        for (String suffix : testSuffixes) {
            String testFileName = baseName + suffix + ".java";
            Path testPath = parent.resolve(testFileName);
            if (Files.exists(testPath)) {
                return true;
            }
        }

        String[] testDirVariants = {"src/test/java", "src/test/groovy"};
        for (String variant : testDirVariants) {
            String testPathStr = sourceFilePath.replace("src/main/java", variant);
            Path testPath = Paths.get(testPathStr);
            if (Files.exists(testPath)) {
                return true;
            }
        }

        return false;
    }
}
