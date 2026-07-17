# Feature: @Bean Method Detection
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F030
# Stories: US068
# Phase 1 draft generated: 2026-07-17
# Last updated: 2026-07-17

Feature: @Bean Method Detection
  Detect @Bean-annotated methods in @Configuration and @SpringBootApplication classes,
  capturing explicit and implicit bean names, return types, and declaring class metadata.

  Background:
    Given Java source files are being analyzed in Pass 2

  Rule: @Bean methods in @Configuration classes are detected

    @US068 @E001 @F030 @must @draft
    Scenario: @Bean with implicit name derived from method name
      Given a @Configuration class with a method annotated @Bean returning MyService
      And the method name is "myService"
      When BeanMethodVisitor analyses the file
      Then a bean method finding is recorded with beanName "myService"
      And the return type is "MyService"
      And the configuration class is recorded

    @US068 @E001 @F030 @must @draft
    Scenario: @Bean with explicit name via single-member annotation
      Given a @Configuration class with a method annotated @Bean("explicitBean")
      When BeanMethodVisitor analyses the file
      Then the bean name is "explicitBean"

    @US068 @E001 @F030 @must @draft
    Scenario: @Bean with explicit name via name attribute
      Given a @Configuration class with a method annotated @Bean(name = "namedBean")
      When BeanMethodVisitor analyses the file
      Then the bean name is "namedBean"

    @US068 @E001 @F030 @must @draft
    Scenario: @Bean with explicit name via value attribute
      Given a @Configuration class with a method annotated @Bean(value = "valuedBean")
      When BeanMethodVisitor analyses the file
      Then the bean name is "valuedBean"

    @US068 @E001 @F030 @should @draft
    Scenario: @Bean with name array uses first element
      Given a @Configuration class with a method annotated @Bean(name = {"primaryName", "alias1", "alias2"})
      When BeanMethodVisitor analyses the file
      Then the bean name is "primaryName"

    @US068 @E001 @F030 @should @draft
    Scenario: Multiple @Bean methods in one configuration class
      Given a @Configuration class with three methods each annotated @Bean
      When BeanMethodVisitor analyses the file
      Then three bean method findings are recorded

    @US068 @E001 @F030 @must @draft
    Scenario: @SpringBootApplication class is treated as configuration
      Given a class annotated @SpringBootApplication with a @Bean method
      When BeanMethodVisitor analyses the file
      Then the bean method finding is recorded

  Rule: Non-configuration classes are not scanned for @Bean

    @US068 @E001 @F030 @must @draft
    Scenario: @Bean in plain @Service class is ignored
      Given a @Service class with a method annotated @Bean
      When BeanMethodVisitor analyses the file
      Then no bean method findings are recorded

    @US068 @E001 @F030 @must @draft
    Scenario: @Configuration interface is skipped
      Given an interface annotated @Configuration with a @Bean method
      When BeanMethodVisitor analyses the file
      Then no bean method findings are recorded

    @US068 @E001 @F030 @should @draft
    Scenario: @Configuration abstract class is skipped
      Given an abstract class annotated @Configuration with a @Bean method
      When BeanMethodVisitor analyses the file
      Then no bean method findings are recorded

    @US068 @E001 @F030 @should @draft
    Scenario: File path is preserved in finding
      Given a @Configuration class with a @Bean method in file "src/main/java/com/example/AppConfig.java"
      When BeanMethodVisitor analyses the file
      Then the finding records filePath "src/main/java/com/example/AppConfig.java"
