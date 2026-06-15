package com.github.ehdez73.code2req.analyzer.db;

import java.util.List;

import com.github.ehdez73.code2req.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.analyzer.db.detector.EntityManagerDetector;
import com.github.ehdez73.code2req.analyzer.db.detector.HibernateSessionDetector;
import com.github.ehdez73.code2req.analyzer.db.detector.JdbcTemplateDetector;
import com.github.ehdez73.code2req.analyzer.db.detector.ProcedureDetector;
import com.github.ehdez73.code2req.analyzer.db.detector.SpringDataJpaDetector;
import com.github.ehdez73.code2req.analyzer.db.detector.TransactionalDetector;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DbAccessVisitorTest {

    private final DbAccessVisitor visitor = new DbAccessVisitor(List.of(
        new JdbcTemplateDetector(),
        new EntityManagerDetector(),
        new HibernateSessionDetector(),
        new ProcedureDetector(),
        new TransactionalDetector(),
        new SpringDataJpaDetector()
    ));

    private AnalysisResult analyze(String filePath, String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        visitor.analyze(cu, builder, new AnalysisContext(filePath));
        return builder.build(filePath);
    }

    @Test
    void jdbcTemplateQuery_extractsSqlAndTableHint() {
        AnalysisResult result = analyze("OrderDao.java", """
            import org.springframework.jdbc.core.JdbcTemplate;
            import org.springframework.stereotype.Repository;
            @Repository
            public class OrderDao {
                private final JdbcTemplate jdbcTemplate;
                public OrderDao(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
                public void findOrders() {
                    jdbcTemplate.query("SELECT * FROM orders WHERE id = ?", rowMapper, id);
                }
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        DbAccessInfo info = result.findings(DbAccessInfo.class).getFirst();
        assertEquals(DbAccessType.JDBC_TEMPLATE_QUERY.name(), info.type());
        assertEquals("SELECT * FROM orders WHERE id = ?", info.sql());
        assertEquals("orders", info.tableHint());
        assertEquals("findOrders", info.methodName());
        assertEquals("OrderDao", info.className());
    }

    @Test
    void jdbcTemplateUpdate_extractsSql() {
        AnalysisResult result = analyze("OrderDao.java", """
            import org.springframework.jdbc.core.JdbcTemplate;
            import org.springframework.stereotype.Repository;
            @Repository
            public class OrderDao {
                private final JdbcTemplate jdbcTemplate;
                public OrderDao(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
                public void updateOrder() {
                    jdbcTemplate.update("UPDATE orders SET status = ? WHERE id = ?", status, id);
                }
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        DbAccessInfo info = result.findings(DbAccessInfo.class).getFirst();
        assertEquals(DbAccessType.JDBC_TEMPLATE_UPDATE.name(), info.type());
        assertEquals("UPDATE orders SET status = ? WHERE id = ?", info.sql());
        assertEquals("updateOrder", info.methodName());
    }

    @Test
    void jdbcTemplateQueryForObject_detected() {
        AnalysisResult result = analyze("UserDao.java", """
            import org.springframework.jdbc.core.JdbcTemplate;
            import org.springframework.stereotype.Repository;
            @Repository
            public class UserDao {
                private final JdbcTemplate jdbcTemplate;
                public UserDao(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
                public String getUserName() {
                    return jdbcTemplate.queryForObject("SELECT name FROM users WHERE id = ?", String.class, id);
                }
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        DbAccessInfo info = result.findings(DbAccessInfo.class).getFirst();
        assertEquals(DbAccessType.JDBC_TEMPLATE_QUERY.name(), info.type());
        assertEquals("SELECT name FROM users WHERE id = ?", info.sql());
        assertEquals("users", info.tableHint());
    }

    @Test
    void jdbcTemplateBatchUpdate_detected() {
        AnalysisResult result = analyze("BatchDao.java", """
            import org.springframework.jdbc.core.JdbcTemplate;
            import org.springframework.stereotype.Repository;
            @Repository
            public class BatchDao {
                private final JdbcTemplate jdbcTemplate;
                public BatchDao(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
                public void batchUpdate() {
                    jdbcTemplate.batchUpdate("INSERT INTO logs VALUES (?, ?)", batchArgs);
                }
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        assertEquals(DbAccessType.JDBC_TEMPLATE_UPDATE.name(),
            result.findings(DbAccessInfo.class).getFirst().type());
    }

    @Test
    void procedureAnnotationWithName_capturesProcedureName() {
        AnalysisResult result = analyze("TaxRepository.java", """
            import org.springframework.data.jpa.repository.query.Procedure;
            import org.springframework.data.jpa.repository.JpaRepository;
            public interface TaxRepository extends JpaRepository<Tax, Long> {
                @Procedure(name = "PR_CALCULATE_TAX")
                void calculateTax();
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        DbAccessInfo info = result.findings(DbAccessInfo.class).getFirst();
        assertEquals(DbAccessType.PROCEDURE.name(), info.type());
        assertEquals("PR_CALCULATE_TAX", info.procedureName());
        assertEquals("calculateTax", info.methodName());
    }

    @Test
    void procedureAnnotationWithValue_capturesProcedureName() {
        AnalysisResult result = analyze("AuditRepository.java", """
            import org.springframework.data.jpa.repository.query.Procedure;
            import org.springframework.data.jpa.repository.JpaRepository;
            public interface AuditRepository extends JpaRepository<Audit, Long> {
                @Procedure("PR_AUDIT_LOG")
                void audit();
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        assertEquals("PR_AUDIT_LOG",
            result.findings(DbAccessInfo.class).getFirst().procedureName());
    }

    @Test
    void methodLevelTransactional_recordsTransactionRoot() {
        AnalysisResult result = analyze("OrderService.java", """
            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;
            @Service
            public class OrderService {
                @Transactional
                public void placeOrder() {}
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        DbAccessInfo info = result.findings(DbAccessInfo.class).getFirst();
        assertEquals(DbAccessType.TRANSACTIONAL.name(), info.type());
        assertTrue(info.isTransactionRoot());
        assertEquals("placeOrder", info.methodName());
    }

    @Test
    void classLevelTransactional_allPublicMethodsInherit() {
        AnalysisResult result = analyze("PaymentService.java", """
            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;
            @Service
            @Transactional
            public class PaymentService {
                public void processPayment() {}
                public void refund() {}
                private void internalHelper() {}
            }
            """);

        assertEquals(2, result.findings(DbAccessInfo.class).size());
        for (DbAccessInfo info : result.findings(DbAccessInfo.class)) {
            assertEquals(DbAccessType.TRANSACTIONAL.name(), info.type());
            assertTrue(info.isTransactionRoot());
        }
        assertTrue(result.findings(DbAccessInfo.class).stream()
            .anyMatch(i -> "processPayment".equals(i.methodName())));
        assertTrue(result.findings(DbAccessInfo.class).stream()
            .anyMatch(i -> "refund".equals(i.methodName())));
    }

    @Test
    void jpaRepositoryInterface_capturesEntityAndDerivedQueries() {
        AnalysisResult result = analyze("OrderRepository.java", """
            import org.springframework.data.jpa.repository.JpaRepository;
            import org.springframework.stereotype.Repository;
            public interface OrderRepository extends JpaRepository<Order, Long> {
                Order findByCustomerName(String name);
                List<Order> findAllByOrderByStatusAsc();
                void deleteByStatus(String status);
            }
            """);

        assertEquals(3, result.findings(DbAccessInfo.class).size());
        for (DbAccessInfo info : result.findings(DbAccessInfo.class)) {
            assertEquals(DbAccessType.SPRING_DATA.name(), info.type());
            assertEquals("Order", info.entityType());
        }
    }

    @Test
    void crudRepositoryInterface_capturesEntityType() {
        AnalysisResult result = analyze("UserRepository.java", """
            import org.springframework.data.repository.CrudRepository;
            public interface UserRepository extends CrudRepository<User, Long> {
                User findByEmail(String email);
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        DbAccessInfo info = result.findings(DbAccessInfo.class).getFirst();
        assertEquals(DbAccessType.SPRING_DATA.name(), info.type());
        assertEquals("User", info.entityType());
        assertEquals("findByEmail", info.methodName());
    }

    @Test
    void springDataSkipQueryAnnotation() {
        AnalysisResult result = analyze("OrderRepository.java", """
            import org.springframework.data.jpa.repository.JpaRepository;
            import org.springframework.data.jpa.repository.Query;
            public interface OrderRepository extends JpaRepository<Order, Long> {
                Order findByStatus(String status);
                @Query("SELECT o FROM Order o WHERE o.name = :name")
                Order findCustom(String name);
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        assertEquals("findByStatus",
            result.findings(DbAccessInfo.class).getFirst().methodName());
    }

    @Test
    void entityManagerCreateQuery_extractsJpql() {
        AnalysisResult result = analyze("OrderService.java", """
            import org.springframework.stereotype.Service;
            import jakarta.persistence.EntityManager;
            import jakarta.persistence.PersistenceContext;
            @Service
            public class OrderService {
                @PersistenceContext
                private EntityManager entityManager;
                public void findOrders() {
                    entityManager.createQuery("SELECT o FROM Order o WHERE o.status = :status");
                }
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        DbAccessInfo info = result.findings(DbAccessInfo.class).getFirst();
        assertEquals(DbAccessType.ENTITY_MANAGER.name(), info.type());
        assertEquals("SELECT o FROM Order o WHERE o.status = :status", info.sql());
        assertEquals("findOrders", info.methodName());
    }

    @Test
    void entityManagerPersist_detected() {
        AnalysisResult result = analyze("OrderService.java", """
            import org.springframework.stereotype.Service;
            import jakarta.persistence.EntityManager;
            import jakarta.persistence.PersistenceContext;
            @Service
            public class OrderService {
                @PersistenceContext
                private EntityManager entityManager;
                public void saveOrder(Order order) {
                    entityManager.persist(order);
                }
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        assertEquals(DbAccessType.ENTITY_MANAGER.name(),
            result.findings(DbAccessInfo.class).getFirst().type());
        assertEquals("saveOrder",
            result.findings(DbAccessInfo.class).getFirst().methodName());
    }

    @Test
    void entityManagerMergeAndRemove_detected() {
        AnalysisResult result = analyze("OrderService.java", """
            import org.springframework.stereotype.Service;
            import jakarta.persistence.EntityManager;
            import jakarta.persistence.PersistenceContext;
            @Service
            public class OrderService {
                @PersistenceContext
                private EntityManager entityManager;
                public void updateOrder(Order order) {
                    entityManager.merge(order);
                }
                public void deleteOrder(Long id) {
                    entityManager.remove(entityManager.find(Order.class, id));
                }
            }
            """);

        assertEquals(3, result.findings(DbAccessInfo.class).size());
        assertTrue(result.findings(DbAccessInfo.class).stream()
            .allMatch(i -> DbAccessType.ENTITY_MANAGER.name().equals(i.type())));
    }

    @Test
    void hibernateSessionSave_detected() {
        AnalysisResult result = analyze("OrderDao.java", """
            import org.hibernate.Session;
            import org.hibernate.SessionFactory;
            import org.springframework.stereotype.Repository;
            @Repository
            public class OrderDao {
                private final SessionFactory sessionFactory;
                public OrderDao(SessionFactory sf) { this.sessionFactory = sf; }
                public void saveOrder(Order order) {
                    sessionFactory.getCurrentSession().save(order);
                }
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        assertEquals(DbAccessType.HIBERNATE_SESSION.name(),
            result.findings(DbAccessInfo.class).getFirst().type());
        assertEquals("saveOrder",
            result.findings(DbAccessInfo.class).getFirst().methodName());
    }

    @Test
    void hibernateSessionCreateQuery_extractsHql() {
        AnalysisResult result = analyze("OrderDao.java", """
            import org.hibernate.Session;
            import org.springframework.stereotype.Repository;
            @Repository
            public class OrderDao {
                private final Session session;
                public OrderDao(Session session) { this.session = session; }
                public void findOrders() {
                    session.createQuery("from Order where status = :status");
                }
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        DbAccessInfo info = result.findings(DbAccessInfo.class).getFirst();
        assertEquals(DbAccessType.HIBERNATE_SESSION.name(), info.type());
        assertEquals("from Order where status = :status", info.sql());
    }

    @Test
    void hibernateSessionCreateNativeQuery_detected() {
        AnalysisResult result = analyze("ReportDao.java", """
            import org.hibernate.Session;
            import org.springframework.stereotype.Repository;
            @Repository
            public class ReportDao {
                private final Session session;
                public ReportDao(Session session) { this.session = session; }
                public void runReport() {
                    session.createNativeQuery("SELECT * FROM reports WHERE id = ?");
                }
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        DbAccessInfo info = result.findings(DbAccessInfo.class).getFirst();
        assertEquals(DbAccessType.HIBERNATE_SESSION.name(), info.type());
        assertEquals("SELECT * FROM reports WHERE id = ?", info.sql());
    }

    @Test
    void hibernateSessionGetAndDelete_detected() {
        AnalysisResult result = analyze("OrderDao.java", """
            import org.hibernate.Session;
            import org.springframework.stereotype.Repository;
            @Repository
            public class OrderDao {
                private final Session session;
                public OrderDao(Session session) { this.session = session; }
                public void deleteOrder(Long id) {
                    Order order = session.get(Order.class, id);
                    if (order != null) session.delete(order);
                }
            }
            """);

        assertEquals(2, result.findings(DbAccessInfo.class).size());
        assertTrue(result.findings(DbAccessInfo.class).stream()
            .allMatch(i -> DbAccessType.HIBERNATE_SESSION.name().equals(i.type())));
    }

    @Test
    void noDbAnnotations_producesEmpty() {
        AnalysisResult result = analyze("PlainComponent.java", """
            import org.springframework.stereotype.Component;
            @Component
            public class PlainComponent {
                public void doSomething() {}
            }
            """);

        assertTrue(result.findings(DbAccessInfo.class).isEmpty());
    }

    @Test
    void multipleJdbcTemplateCalls_allCaptured() {
        AnalysisResult result = analyze("OrderDao.java", """
            import org.springframework.jdbc.core.JdbcTemplate;
            import org.springframework.stereotype.Repository;
            @Repository
            public class OrderDao {
                private final JdbcTemplate jdbcTemplate;
                public OrderDao(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
                public void processOrders() {
                    jdbcTemplate.query("SELECT * FROM orders", rowMapper);
                    jdbcTemplate.update("UPDATE orders SET status = ?", status);
                }
            }
            """);

        assertEquals(2, result.findings(DbAccessInfo.class).size());
        assertEquals(1, result.findings(DbAccessInfo.class).stream()
            .filter(i -> DbAccessType.JDBC_TEMPLATE_QUERY.name().equals(i.type())).count());
        assertEquals(1, result.findings(DbAccessInfo.class).stream()
            .filter(i -> DbAccessType.JDBC_TEMPLATE_UPDATE.name().equals(i.type())).count());
    }

    @Test
    void jdbcTemplateWithEmShortName_detected() {
        AnalysisResult result = analyze("OrderService.java", """
            import org.springframework.stereotype.Service;
            import jakarta.persistence.EntityManager;
            import jakarta.persistence.PersistenceContext;
            @Service
            public class OrderService {
                @PersistenceContext
                private EntityManager em;
                public void findOrders() {
                    em.createQuery("SELECT o FROM Order o");
                }
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        assertEquals(DbAccessType.ENTITY_MANAGER.name(),
            result.findings(DbAccessInfo.class).getFirst().type());
    }

    @Test
    void hibernateSessionByNaturalId_detected() {
        AnalysisResult result = analyze("OrderDao.java", """
            import org.hibernate.Session;
            import org.springframework.stereotype.Repository;
            @Repository
            public class OrderDao {
                private final Session session;
                public OrderDao(Session session) { this.session = session; }
                public void findByNaturalKey(String key) {
                    session.byNaturalId(Order.class);
                }
            }
            """);

        assertEquals(1, result.findings(DbAccessInfo.class).size());
        assertEquals(DbAccessType.HIBERNATE_SESSION.name(),
            result.findings(DbAccessInfo.class).getFirst().type());
    }

    @Test
    void multipleSpringDataMethods_allCaptured() {
        AnalysisResult result = analyze("OrderRepository.java", """
            import org.springframework.data.jpa.repository.JpaRepository;
            public interface OrderRepository extends JpaRepository<Order, Long> {
                List<Order> findByStatus(String status);
                Optional<Order> findById(Long id);
                Order save(Order order);
                void delete(Order order);
                long count();
            }
            """);

        assertEquals(5, result.findings(DbAccessInfo.class).size());
        assertTrue(result.findings(DbAccessInfo.class).stream()
            .allMatch(i -> DbAccessType.SPRING_DATA.name().equals(i.type())));
        assertTrue(result.findings(DbAccessInfo.class).stream()
            .allMatch(i -> "Order".equals(i.entityType())));
    }

    @Test
    void tableHintInference_multiplePatterns() {
        AnalysisResult result = analyze("Dao.java", """
            import org.springframework.jdbc.core.JdbcTemplate;
            import org.springframework.stereotype.Repository;
            @Repository
            public class Dao {
                private final JdbcTemplate jdbcTemplate;
                public Dao(JdbcTemplate jt) { this.jdbcTemplate = jt; }
                public void doQueries() {
                    jdbcTemplate.query("SELECT * FROM products WHERE id = ?", rm, id);
                    jdbcTemplate.update("INSERT INTO audit_log VALUES (?)", val);
                    jdbcTemplate.update("DELETE FROM sessions WHERE expired = true");
                }
            }
            """);

        var findings = result.findings(DbAccessInfo.class);
        assertEquals("products", findings.get(0).tableHint());
        assertEquals("audit_log", findings.get(1).tableHint());
        assertEquals("sessions", findings.get(2).tableHint());
    }
}
