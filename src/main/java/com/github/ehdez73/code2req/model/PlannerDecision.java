package com.github.ehdez73.code2req.model;

import java.util.List;

public record PlannerDecision(
    String taskId,
    String filePath,
    boolean qualified,
    List<QualificationReason> reasons
) {
    public static PlannerDecision notQualified(String taskId, String filePath) {
        return new PlannerDecision(taskId, filePath, false, List.of(QualificationReason.NONE));
    }

    public static PlannerDecision qualified(String taskId, String filePath, List<QualificationReason> reasons) {
        return new PlannerDecision(taskId, filePath, true, reasons);
    }
}
