# Feature: Database Access Detection
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F011
# Stories: US031
# Phase 1 draft generated: 2026-06-15
# Last updated: 2026-06-15

Feature: Database Access Detection
  Detect JDBC template queries, stored procedure calls, entity manager usage, and transaction boundaries.

  Background:
    Given Java source files are being analysed in Pass 2

  Rule: JdbcTemplate invocations are captured with their SQL strings

    @US031 @E001 @F011 @must @draft
    Scenario: Service uses JdbcTemplate.query()
      Given a @Service with an injected JdbcTemplate
      And a method calls jdbcTemplate.query("SELECT * FROM orders WHERE id = ?", rowMapper, id)
      When DbAccessVisitor analyses the method
      Then a database access entry is recorded with type JDBCTEMPLATE_QUERY
      And the SQL string "SELECT * FROM orders WHERE id = ?" is captured
      And the table hint "orders" is extracted from the SQL

    @US031 @E001 @F011 @must @draft
    Scenario: Service uses JdbcTemplate.update()
      Given a method calling jdbcTemplate.update("UPDATE orders SET status = ? WHERE id = ?", status, id)
      When DbAccessVisitor analyses the method
      Then a database access entry is recorded with type JDBCTEMPLATE_UPDATE
      And the SQL string is captured

  Rule: @Procedure annotations are captured

    @US031 @E001 @F011 @must @draft
    Scenario: Repository method annotated with @Procedure
      Given a @Repository interface with a method annotated @Procedure(name = "PR_CALCULATE_TAX")
      When DbAccessVisitor analyses the file
      Then a database access entry is recorded with type PROCEDURE
      And the procedure name "PR_CALCULATE_TAX" is captured

  Rule: @Transactional boundaries are recorded

    @US031 @E001 @F011 @should @draft
    Scenario: Method annotated with @Transactional
      Given a service method annotated with @Transactional
      When DbAccessVisitor analyses the method
      Then a transactional boundary entry is recorded
      And the method is marked as a transaction root

    @US031 @E001 @F011 @should @draft
    Scenario: Class annotated with @Transactional
      Given a @Service class annotated with @Transactional
      When DbAccessVisitor analyses the class
      Then all public methods inherit the transactional boundary

  Rule: Spring Data interfaces are registered as virtual database access points

    @US031 @E001 @F011 @should @draft
    Scenario: JpaRepository interface derived query method
      Given an interface extending JpaRepository<Order, Long>
      And a method declaration findByCustomerName(String name)
      When DbAccessVisitor analyses the interface
      Then a virtual database access entry is recorded with type SPRING_DATA
      And the entity type "Order" is inferred
      And the derived query method "findByCustomerName" is captured

  Rule: EntityManager calls are detected

    @US031 @E001 @F011 @should @draft
    Scenario: Service uses EntityManager
      Given a @Service using @PersistenceContext EntityManager em
      And a method calling em.createQuery("SELECT o FROM Order o WHERE o.status = :status")
      When DbAccessVisitor analyses the method
      Then a database access entry is recorded with type ENTITY_MANAGER
      And the JPQL string is captured
