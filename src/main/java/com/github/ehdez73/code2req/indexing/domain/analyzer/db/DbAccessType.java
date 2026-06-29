package com.github.ehdez73.code2req.indexing.domain.analyzer.db;

public enum DbAccessType {
    JDBC_TEMPLATE_QUERY,
    JDBC_TEMPLATE_UPDATE,
    PROCEDURE,
    TRANSACTIONAL,
    SPRING_DATA,
    ENTITY_MANAGER,
    HIBERNATE_SESSION,
    NATIVE_SQL,
    JPQL_HQL
}
