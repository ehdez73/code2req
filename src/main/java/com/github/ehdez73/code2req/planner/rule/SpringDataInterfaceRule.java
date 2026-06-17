package com.github.ehdez73.code2req.planner.rule;

import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.store.FindingType;
import org.springframework.stereotype.Component;

@Component
public class SpringDataInterfaceRule extends AbstractFindingTypeRule {
    public SpringDataInterfaceRule() {
        super(QualificationReason.SPRING_DATA_INTERFACE, FindingType.SPRING_DATA_INTERFACE);
    }
}
