package com.github.ehdez73.code2req.enrichment.domain.model;

public enum QualificationReason {
    UNRESOLVED_SIGNATURES_EXCEEDED,
    STORED_PROCEDURE_CALL,
    CUSTOM_CONSTRAINT_VALIDATOR,
    BEAN_VALIDATION,
    TEST_ASSERTIONS_PRESENT,
    UNRESOLVED_FLOATING_LINK,
    NONE
}
