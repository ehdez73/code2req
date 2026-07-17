# Feature: Spring XML Configuration Analysis
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F029
# Stories: US065, US066, US067
# Phase 1 draft generated: 2026-07-17
# Last updated: 2026-07-17

Feature: Spring XML Configuration Analysis
  Parse Spring XML configuration files to extract bean declarations, component scans,
  AOP configuration, JMS listeners, scheduled tasks, namespace beans, alias definitions,
  and transitive import resolution.

  Background:
    Given Spring XML configuration files exist under scan target directories
    And the XML files have the beans namespace root element

  Rule: Bean declarations and aliases are captured from <bean> and <alias> elements

    @US065 @E001 @F029 @must @final
    Scenario: Standard bean declaration with id and class
      Given a Spring XML file with a <bean id="orderService" class="com.example.OrderService"/>
      When XmlBeanAnalyzer analyses the file
      Then a bean finding is recorded with beanId "orderService"
      And the class is "com.example.OrderService"
      And the scope defaults to "singleton"

    @US065 @E001 @F029 @must @final
    Scenario: Bean with explicit scope and factory-method
      Given a Spring XML file with a <bean id="prototypeBean" class="com.example.Builder" scope="prototype" factory-method="create"/>
      When XmlBeanAnalyzer analyses the file
      Then a bean finding is recorded with beanId "prototypeBean"
      And the scope is "prototype"
      And the factory-method is "create"

    @US065 @E001 @F029 @must @final
    Scenario: Alias mapping is captured
      Given a Spring XML file with an <alias name="orderService" alias="orderSvc"/>
      When XmlBeanAnalyzer analyses the file
      Then a bean finding is recorded with beanId "orderSvc"
      And the target name is "orderService"

    @US065 @E001 @F029 @should @final
    Scenario: Non-Spring XML is silently skipped
      Given an XML file with a root element not in the beans namespace, such as a Maven POM
      When XmlBeanAnalyzer analyses the file
      Then no findings are produced
      And no error is raised

    @US065 @E001 @F029 @should @final
    Scenario: Nested beans element with profile attribute is recursed
      Given a Spring XML file with <beans profile="dev"><bean id="devBean" class="com.example.Dev"/></beans>
      When XmlBeanAnalyzer analyses the file
      Then a bean finding is recorded with beanId "devBean"

  Rule: Known namespace elements are resolved to Java types

    @US065 @E001 @F029 @should @final
    Scenario: util namespace element with resolved type
      Given a Spring XML file with <util:list id="myList"><value>a</value><value>b</value></util:list>
      When XmlBeanAnalyzer analyses the file
      Then a namespace bean finding is recorded with beanId "myList"
      And the resolved type is "java.util.List"

    @US065 @E001 @F029 @should @final
    Scenario: context:component-scan base package is captured
      Given a Spring XML file with <context:component-scan base-package="com.example.service"/>
      When XmlBeanAnalyzer analyses the file
      Then a component-scan finding is recorded with basePackage "com.example.service"

    @US065 @E001 @F029 @should @final
    Scenario: jdbc namespace element is resolved
      Given a Spring XML file with <jdbc:embedded-database id="dataSource"/>
      When XmlBeanAnalyzer analyses the file
      Then a namespace bean finding is recorded with beanId "dataSource"
      And the resolved type is "javax.sql.DataSource"

  Rule: Scheduled tasks and JMS listeners from XML are captured

    @US066 @E001 @F029 @should @final
    Scenario: Task scheduled with cron expression
      Given a Spring XML file with <task:scheduled ref="reportSvc" method="generate" cron="0 0 0 * * ?"/>
      When XmlBeanAnalyzer analyses the file
      Then a scheduled task finding is recorded with ref "reportSvc"
      And the method is "generate"
      And the cron expression is "0 0 0 * * ?"
      And the task type is "cron"

    @US066 @E001 @F029 @should @final
    Scenario: Task scheduled with fixed-rate
      Given a Spring XML file with <task:scheduled ref="healthSvc" method="check" fixed-rate="60000"/>
      When XmlBeanAnalyzer analyses the file
      Then a scheduled task finding is recorded with fixed-rate "60000"
      And the task type is "fixed-rate"

    @US066 @E001 @F029 @should @final
    Scenario: JMS listener captured
      Given a Spring XML file with <jms:listener destination="order.queue" ref="orderListener" method="onOrder"/>
      When XmlBeanAnalyzer analyses the file
      Then a JMS listener finding is recorded with destination "order.queue"
      And the bean reference is "orderListener"
      And the method is "onOrder"

    @US066 @E001 @F029 @should @final
    Scenario: AOP config is flagged
      Given a Spring XML file with <aop:config><aop:pointcut id="pc" expression="execution(* com.example..*(..))"/></aop:config>
      When XmlBeanAnalyzer analyses the file
      Then an AOP config finding is recorded

  Rule: Import chains are resolved transitively with cycle detection

    @US067 @E001 @F029 @should @final
    Scenario: Imported XML file is analyzed recursively
      Given a Spring XML file containing <import resource="db-config.xml"/>
      And db-config.xml contains a <bean id="dataSource" class="com.example.DataSource"/>
      When XmlBeanAnalyzer analyses the parent file
      Then the bean "dataSource" from the imported file is included in the findings

    @US067 @E001 @F029 @should @final
    Scenario: Circular import is detected and halted
      Given a.xml imports b.xml and b.xml imports a.xml
      When XmlBeanAnalyzer analyses a.xml
      Then no infinite recursion occurs
      And the findings from the first visit to each file are included

    @US067 @E001 @F029 @should @final
    Scenario: Classpath and file resource prefixes are resolved
      Given a Spring XML file with <import resource="classpath:common/beans.xml"/>
      When XmlBeanAnalyzer analyses the file
      Then the resource is resolved relative to source root and resource directories

    @US067 @E001 @F029 @should @final
    Scenario: Glob pattern in import is expanded
      Given a Spring XML file with <import resource="classpath*:config/*-beans.xml"/>
      When XmlBeanAnalyzer analyses the file
      Then all matching files under the config directory are analyzed

  Rule: @ImportResource annotations bridge Java source to XML analysis

    @US067 @E001 @F029 @should @final
    Scenario: Java class with @ImportResource triggers XML analysis
      Given a Java class annotated @ImportResource("classpath:beans.xml")
      And beans.xml contains a <bean id="xmlBean" class="com.example.XmlBean"/>
      When ImportResourceVisitor analyses the Java file
      Then the finding "xmlBean" from the XML is included in the Java file's analysis result

    @US067 @E001 @F029 @should @final
    Scenario: Multiple resource paths in @ImportResource
      Given a Java class annotated @ImportResource({"a.xml", "b.xml"})
      And both a.xml and b.xml contain bean declarations
      When ImportResourceVisitor analyses the Java file
      Then findings from both a.xml and b.xml are included
