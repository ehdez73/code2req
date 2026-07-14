package com.github.ehdez73.code2req.enrichment.domain.model;

import java.util.List;

public record PlannerDecision(
    String taskId,
    String filePath,
    String targetName,
    boolean qualified,
    List<QualificationReason> reasons
) {
    public static PlannerDecision notQualified(String taskId, String filePath, String targetName) {
        return new PlannerDecision(taskId, filePath, targetName, false, List.of(QualificationReason.NONE));
    }

    public static PlannerDecision qualified(String taskId, String filePath, String targetName, List<QualificationReason> reasons) {
        return new PlannerDecision(taskId, filePath, targetName, true, reasons);
    }
}
