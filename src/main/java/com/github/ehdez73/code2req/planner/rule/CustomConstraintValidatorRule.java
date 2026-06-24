package com.github.ehdez73.code2req.planner.rule;

import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.store.FindingType;
import org.springframework.stereotype.Component;

/**
 * Qualifies a task when the file contains a custom Jakarta/Javax ConstraintValidator
 * with a non-trivial {@code isValid} body that requires LLM interpretation.
 */
@Component
public class CustomConstraintValidatorRule extends AbstractFindingTypeRule {
    public CustomConstraintValidatorRule() {
        super(QualificationReason.CUSTOM_CONSTRAINT_VALIDATOR, FindingType.CONSTRAINT_VALIDATOR);
    }
}
