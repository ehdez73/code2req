package com.github.ehdez73.code2req.planner.rule;

import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.store.FindingType;
import org.springframework.stereotype.Component;

@Component
public class CustomConstraintValidatorRule extends AbstractFindingTypeRule {
    public CustomConstraintValidatorRule() {
        super(QualificationReason.CUSTOM_CONSTRAINT_VALIDATOR, FindingType.CONSTRAINT_VALIDATOR);
    }
}
