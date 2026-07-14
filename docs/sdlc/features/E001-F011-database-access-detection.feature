# Feature: Database Access Detection
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F011
# Stories: US031, US059, US060, US061
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

  Rule: @NamedQuery and @NamedNativeQuery declarations are detected

    @US059 @E001 @F011 @should @draft
    Scenario: Entity class with @NamedQuery annotation
      Given an entity class annotated with @NamedQuery(name = "Order.findByStatus", query = "SELECT o FROM Order o WHERE o.status = :status")
      When DbAccessVisitor analyses the file
      Then a database access entry is recorded with type JPQL_HQL
      And the JPQL query string is captured

    @US059 @E001 @F011 @should @draft
    Scenario: Entity class with @NamedQueries container form
      Given an entity class annotated with @NamedQueries({ @NamedQuery(name = "o1", query = "q1"), @NamedQuery(name = "o2", query = "q2") })
      When DbAccessVisitor analyses the file
      Then both named queries are recorded as database access entries

    @US059 @E001 @F011 @should @draft
    Scenario: Entity class with @NamedNativeQuery
      Given an entity class annotated with @NamedNativeQuery(name = "Order.findActive", query = "SELECT * FROM orders WHERE active = 1", resultClass = Order.class)
      When DbAccessVisitor analyses the file
      Then a database access entry is recorded with type NATIVE_SQL
      And the native SQL query string is captured

  Rule: Raw JDBC calls are detected

    @US060 @E001 @F011 @should @draft
    Scenario: Class using Connection.prepareStatement
      Given a class with a method calling connection.prepareStatement("SELECT * FROM orders WHERE id = ?")
      When DbAccessVisitor analyses the method
      Then a database access entry is recorded with type NATIVE_SQL
      And the SQL string is captured

    @US060 @E001 @F011 @should @draft
    Scenario: Class using Statement.executeQuery
      Given a class with a method calling statement.executeQuery("SELECT * FROM orders")
      When DbAccessVisitor analyses the method
      Then a database access entry is recorded with type NATIVE_SQL
      And the SQL string is captured

    @US060 @E001 @F011 @should @draft
    Scenario: Raw JDBC call excludes JdbcTemplate to avoid double-counting
      Given a class with a method calling jdbcTemplate.query("SELECT * FROM orders", ...)
      When DbAccessVisitor analyses the method
      Then no raw JDBC entry is added for the jdbcTemplate call

  Rule: NamedParameterJdbcTemplate and SimpleJdbcCall are detected

    @US061 @E001 @F011 @should @draft
    Scenario: NamedParameterJdbcTemplate query via npjt alias
      Given a class with a method calling npjt.query("SELECT * FROM orders WHERE status = :status", params, rowMapper)
      When DbAccessVisitor analyses the method
      Then a database access entry is recorded with type JDBCTEMPLATE_QUERY
      And the SQL string "SELECT * FROM orders WHERE status = :status" is captured
      And the table hint "orders" is extracted

    @US061 @E001 @F011 @should @draft
    Scenario: SimpleJdbcCall invoke detected
      Given a class with a method calling simpleJdbcCall.withProcedureName("PR_CALCULATE_TAX").execute(params)
      When DbAccessVisitor analyses the method
      Then a database access entry is recorded
      And the procedure reference is captured
