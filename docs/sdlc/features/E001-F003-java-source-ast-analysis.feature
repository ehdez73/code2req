# Feature: Java Source AST Analysis
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F003
# Stories: US006, US007, US008, US009, US010, US023, US026, US027
# Phase 1 draft generated: 2026-06-12
# Last updated: 2026-06-14

Feature: Java Source AST Analysis
  JavaParser-based scanning: component types, HTTP endpoints, event listeners, method calls, custom validators, and scheduled tasks.

  Background:
    Given scan target directories are resolved and accessible
    And only .java files under scan targets are processed

  Rule: JavaParser processes each file independently — a single parse failure does not stop the full scan

    # ---------------------------------------------------------------------------
    # Story US006: Developer discovers application components
    # ---------------------------------------------------------------------------

    @US006 @E001 @F003 @must @draft
    Scenario: Developer scans a project with Spring stereotyped classes
      Given a scan target containing @Controller, @Service, @Repository, and @Component classes
      When the CLI analyzes the Java source files
      Then each Spring stereotype is classified with its annotation type
      And the component inventory includes all identified classes

    @US006 @E001 @F003 @must @draft
    Scenario: Developer scans a file that fails to parse
      Given a scan target containing a Java file with syntax errors
      When the CLI analyzes the Java source files
      Then the failed file is logged with its path and parse error
      And other files in the target continue processing

    @US006 @E001 @F003 @must @draft
    Scenario: Developer scans a project with plain Java classes
      Given a scan target containing classes without Spring stereotypes
      When the CLI analyzes the Java source files
      Then each class is classified as "other" with its package and name

  Rule: Component classification uses annotation presence as the primary heuristic, with structural fallback when type resolution fails

    # ---------------------------------------------------------------------------
    # Story US007: Developer maps HTTP endpoints
    # ---------------------------------------------------------------------------

    @US007 @E001 @F003 @must @draft
    Scenario: Developer scans a controller with multiple endpoint mappings
      Given a @RestController with @GetMapping, @PostMapping, and @RequestMapping methods
      When the CLI extracts HTTP endpoints
      Then each endpoint is captured with its HTTP method and full path
      And class-level and method-level path segments are combined
      And each endpoint is linked to its controller component

    @US007 @E001 @F003 @must @draft
    Scenario: Developer scans a controller with path variables and query parameters
      Given a @RestController method containing @PathVariable and @RequestParam annotations
      When the CLI extracts HTTP endpoints
      Then the path variables and query parameters are captured with the endpoint
      And the endpoint path includes the path variable segment

    @US007 @E001 @F003 @must @draft
    Scenario: Developer scans a controller with no request mappings
      Given a @Controller class with no request mapping annotations on its methods
      When the CLI extracts HTTP endpoints
      Then the class is noted as a controller with zero endpoints
      And no error is raised

    # ---------------------------------------------------------------------------
    # Story US008: Developer traces event listeners
    # ---------------------------------------------------------------------------

    @US008 @E001 @F003 @should @draft
    Scenario: Developer scans a project with @EventListener methods
      Given a component with @EventListener-annotated methods
      When the CLI extracts event listeners
      Then each listener is identified with its event type
      And the listener is linked to its owning component

    @US008 @E001 @F003 @should @draft
    Scenario: Developer scans event handlers with method call chains
      Given an event listener method that calls other service methods
      When the CLI extracts call chains from event handlers
      Then method calls within the handler are captured up to 3 levels deep
      And each call is recorded with target type and method name

    @US008 @E001 @F003 @should @draft
    Scenario: Developer scans a project with ApplicationEventPublisher usage
      Given a component that uses ApplicationEventPublisher to publish events
      When the CLI identifies event publishers
      Then the publisher is linked to the events it publishes

    # ---------------------------------------------------------------------------
    # Story US009: Developer extracts custom validation logic
    # ---------------------------------------------------------------------------

    @US009 @E001 @F003 @should @draft
    Scenario: Developer scans a project with custom @Constraint validators
      Given a field annotated with a custom @Constraint validator
      When the CLI extracts validation logic
      Then the validator class is captured and linked to the field
      And the isValid method body is extracted as a text slice

    @US009 @E001 @F003 @should @draft
    Scenario: Developer scans a project with built-in Jakarta validators
      Given a field annotated with @NotNull or @Size
      When the CLI extracts validation logic
      Then the built-in annotation is noted
      And no isValid body extraction is attempted

    # ---------------------------------------------------------------------------
    # Story US010: Developer discovers scheduled tasks
    # ---------------------------------------------------------------------------

    @US010 @E001 @F003 @should @draft
    Scenario: Developer scans a component with cron-based @Scheduled methods
      Given a component with a @Scheduled method using a cron expression
      When the CLI extracts scheduled tasks
      Then the method is captured with its cron expression
      And the task is categorized as cron-based
      And the method is linked to its owning component

    @US010 @E001 @F003 @should @draft
    Scenario: Developer scans a component with fixed-rate and fixed-delay tasks
      Given a component with @Scheduled methods using fixedRate and fixedDelay
      When the CLI extracts scheduled tasks
      Then each method is captured with its rate or delay value
      And the tasks are categorized as fixed-rate and fixed-delay respectively

  # ---------------------------------------------------------------------------
  # Story US023: Developer traces Kafka event flows
  # ---------------------------------------------------------------------------

  Rule: Kafka listener subscriptions and publications are extracted independently of Spring's in-process event bus

    @US023 @E001 @F003 @should @draft
    Scenario: Developer scans a component with @KafkaListener methods
      Given a component with @KafkaListener-annotated methods specifying topics
      When the CLI extracts Kafka event listeners
      Then each listener is identified with its topic list
      And the listener is linked to its owning component

    @US023 @E001 @F003 @should @draft
    Scenario: Developer scans a @KafkaListener with multiple topics
      Given a @KafkaListener method with topics = {"order-events", "inventory-events"}
      When the CLI extracts Kafka event listeners
      Then all topics in the array are captured
      And the listener is linked to its owning component

    @US023 @E001 @F003 @should @draft
    Scenario: Developer scans a @KafkaListener with topic pattern
      Given a @KafkaListener method using topicPattern attribute
      When the CLI extracts Kafka event listeners
      Then the topic pattern is captured as a text expression

    @US023 @E001 @F003 @should @draft
    Scenario: Developer scans a component publishing to Kafka topics
      Given a component that calls KafkaTemplate.send() with a topic name
      When the CLI extracts outbound event publications
      Then the destination topic is captured with the broker type "KAFKA"
      And the publication is linked to its owning component

    @US023 @E001 @F003 @should @draft
    Scenario: Developer scans a file with Kafka imports but no Kafka annotations
      Given a component that imports Kafka classes but has no @KafkaListener or KafkaTemplate usage
      When the CLI extracts Kafka event listeners and publications
      Then no Kafka listener or publication is recorded
      And no error is raised

  # No error path — parse failures are handled at the file level by US006

  # ---------------------------------------------------------------------------
  # Story US026: Developer traces RabbitMQ event flows
  # ---------------------------------------------------------------------------

  Rule: RabbitMQ listener subscriptions and publications are extracted independently of Spring's in-process event bus

    @US026 @E001 @F003 @should @draft
    Scenario: Developer scans a component with @RabbitListener methods
      Given a component with @RabbitListener-annotated methods specifying queues
      When the CLI extracts RabbitMQ event listeners
      Then each listener is identified with its queue list
      And the listener is linked to its owning component

    @US026 @E001 @F003 @should @draft
    Scenario: Developer scans a @RabbitListener with multiple queues
      Given a @RabbitListener method with queues = {"order.queue", "notification.queue"}
      When the CLI extracts RabbitMQ event listeners
      Then all queues in the array are captured
      And the listener is linked to its owning component

    @US026 @E001 @F003 @should @draft
    Scenario: Developer scans a component publishing to RabbitMQ exchanges
      Given a component that calls RabbitTemplate.convertAndSend() with an exchange and routing key
      When the CLI extracts outbound event publications
      Then the exchange and routing key are captured with the broker type "RABBITMQ"
      And the publication is linked to its owning component

    @US026 @E001 @F003 @should @draft
    Scenario: Developer scans a component publishing via RabbitTemplate.send()
      Given a component that calls RabbitTemplate.send() with an exchange and routing key
      When the CLI extracts outbound event publications
      Then the exchange and routing key are captured with the broker type "RABBITMQ"
      And the publication is linked to its owning component

    @US026 @E001 @F003 @should @draft
    Scenario: Developer scans a file with RabbitMQ imports but no RabbitMQ usage
      Given a component that imports RabbitMQ classes but has no @RabbitListener or RabbitTemplate usage
      When the CLI extracts RabbitMQ event listeners and publications
      Then no RabbitMQ listener or publication is recorded
      And no error is raised

  # ---------------------------------------------------------------------------
  # Story US027: Developer traces ActiveMQ (JMS) event flows
  # ---------------------------------------------------------------------------

  Rule: ActiveMQ JMS listener subscriptions and publications are extracted independently of Spring's in-process event bus

    @US027 @E001 @F003 @should @draft
    Scenario: Developer scans a component with @JmsListener methods
      Given a component with @JmsListener-annotated methods specifying destinations
      When the CLI extracts ActiveMQ event listeners
      Then each listener is identified with its destination
      And the listener is linked to its owning component

    @US027 @E001 @F003 @should @draft
    Scenario: Developer scans a component publishing via JmsTemplate
      Given a component that calls JmsTemplate.convertAndSend() with a destination
      When the CLI extracts outbound event publications
      Then the destination is captured with the broker type "ACTIVEMQ"
      And the publication is linked to its owning component

    @US027 @E001 @F003 @should @draft
    Scenario: Developer scans a file with JMS imports but no JMS usage
      Given a component that imports JMS classes but has no @JmsListener or JmsTemplate usage
      When the CLI extracts ActiveMQ event listeners and publications
      Then no ActiveMQ listener or publication is recorded
      And no error is raised
