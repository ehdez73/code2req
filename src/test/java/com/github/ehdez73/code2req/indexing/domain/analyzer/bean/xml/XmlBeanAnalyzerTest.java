package com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XmlBeanAnalyzerTest {

    private final XmlBeanAnalyzer analyzer = new XmlBeanAnalyzer();

    private Path resourcePath(String name) {
        return Path.of("src/test/resources/xml", name).toAbsolutePath().normalize();
    }

    @Test
    void detectsSimpleBeanElements() {
        List<AnalysisFinding> findings = analyzer.analyze(resourcePath("simple-beans.xml"));

        List<XmlBeanInfo> beans = findings.stream()
            .filter(f -> f instanceof XmlBeanInfo)
            .map(f -> (XmlBeanInfo) f)
            .toList();

        // 5 <bean> elements + 1 <alias> = 6 total
        assertEquals(6, beans.size());

        XmlBeanInfo myService = beans.get(0);
        assertEquals("myService", myService.beanId());
        assertEquals("com.example.MyService", myService.className());
        assertEquals("singleton", myService.scope());
        assertNull(myService.factoryMethod());
    }

    @Test
    void detectsBeanScopeAndFactoryMethod() {
        List<AnalysisFinding> findings = analyzer.analyze(resourcePath("simple-beans.xml"));

        List<XmlBeanInfo> beans = findings.stream()
            .filter(f -> f instanceof XmlBeanInfo)
            .map(f -> (XmlBeanInfo) f)
            .toList();

        XmlBeanInfo myRepo = beans.get(1);
        assertEquals("myRepo", myRepo.beanId());
        assertEquals("prototype", myRepo.scope());

        XmlBeanInfo myFactory = beans.get(2);
        assertEquals("myFactory", myFactory.beanId());
        assertEquals("com.example.MyFactory", myFactory.className());
        assertEquals("createInstance", myFactory.factoryMethod());
    }

    @Test
    void handlesBeanWithoutClass() {
        List<AnalysisFinding> findings = analyzer.analyze(resourcePath("simple-beans.xml"));

        List<XmlBeanInfo> beans = findings.stream()
            .filter(f -> f instanceof XmlBeanInfo)
            .map(f -> (XmlBeanInfo) f)
            .toList();

        XmlBeanInfo noClass = beans.get(3);
        assertEquals("noClassBean", noClass.beanId());
        assertEquals("noClassBean", noClass.className()); // className() falls back to beanId
        assertNull(noClass.factoryMethod());
    }

    @Test
    void handlesBeanWithoutId() {
        List<AnalysisFinding> findings = analyzer.analyze(resourcePath("simple-beans.xml"));

        List<XmlBeanInfo> beans = findings.stream()
            .filter(f -> f instanceof XmlBeanInfo)
            .map(f -> (XmlBeanInfo) f)
            .toList();

        XmlBeanInfo noId = beans.get(4);
        assertEquals("", noId.beanId());
        assertEquals("com.example.NoIdBean", noId.className());
    }

    @Test
    void detectsAliasAsBeanEntry() {
        List<AnalysisFinding> findings = analyzer.analyze(resourcePath("simple-beans.xml"));

        List<XmlBeanInfo> aliases = findings.stream()
            .filter(f -> f instanceof XmlBeanInfo info && "myServiceAlias".equals(info.beanId()))
            .map(f -> (XmlBeanInfo) f)
            .toList();

        assertEquals(1, aliases.size());
        assertEquals("myService", aliases.get(0).className());
    }

    @Test
    void detectsNamespaceElements() {
        List<AnalysisFinding> findings = analyzer.analyze(resourcePath("namespace-beans.xml"));

        List<XmlNamespaceBeanInfo> namespaceBeans = findings.stream()
            .filter(f -> f instanceof XmlNamespaceBeanInfo)
            .map(f -> (XmlNamespaceBeanInfo) f)
            .toList();

        assertEquals(3, namespaceBeans.size());

        XmlNamespaceBeanInfo constant = namespaceBeans.get(0);
        assertEquals("javaVersion", constant.beanId());
        assertEquals("http://www.springframework.org/schema/util", constant.namespaceUri());
        assertEquals("constant", constant.elementName());
        assertNull(constant.resolvedType());

        XmlNamespaceBeanInfo ds = namespaceBeans.get(1);
        assertEquals("dataSource", ds.beanId());
        assertEquals("http://www.springframework.org/schema/jdbc", ds.namespaceUri());
        assertEquals("embedded-database", ds.elementName());
        assertEquals("javax.sql.DataSource", ds.resolvedType());

        XmlNamespaceBeanInfo scheduler = namespaceBeans.get(2);
        assertEquals("taskScheduler", scheduler.beanId());
        assertEquals("http://www.springframework.org/schema/task", scheduler.namespaceUri());
        assertEquals("scheduler", scheduler.elementName());
        assertEquals("org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler", scheduler.resolvedType());
    }

    @Test
    void detectsComponentScan() {
        List<AnalysisFinding> findings = analyzer.analyze(resourcePath("namespace-beans.xml"));

        List<XmlComponentScanInfo> scans = findings.stream()
            .filter(f -> f instanceof XmlComponentScanInfo)
            .map(f -> (XmlComponentScanInfo) f)
            .toList();

        assertEquals(1, scans.size());
        assertEquals("com.example", scans.get(0).basePackage());
    }

    @Test
    void detectsAopConfig() {
        List<AnalysisFinding> findings = analyzer.analyze(resourcePath("namespace-beans.xml"));

        List<XmlAopConfigInfo> aopConfigs = findings.stream()
            .filter(f -> f instanceof XmlAopConfigInfo)
            .map(f -> (XmlAopConfigInfo) f)
            .toList();

        assertEquals(3, aopConfigs.size());
    }

    @Test
    void ignoresNonSpringXml() {
        List<AnalysisFinding> findings = analyzer.analyze(resourcePath("non-spring.xml"));

        assertTrue(findings.isEmpty());
    }

    @Test
    void ignoresNonexistentFile() {
        List<AnalysisFinding> findings = analyzer.analyze(Path.of("nonexistent.xml"));

        assertTrue(findings.isEmpty());
    }

    @Test
    void followsImportChain() {
        List<AnalysisFinding> findings = analyzer.analyze(resourcePath("importing-beans.xml"));

        // Should contain beans from both importing-beans.xml and imported simple-beans.xml
        List<XmlBeanInfo> beans = findings.stream()
            .filter(f -> f instanceof XmlBeanInfo)
            .map(f -> (XmlBeanInfo) f)
            .toList();

        // simple-beans.xml has 5 beans + 1 alias = 6, importing-beans.xml has 1 more = 7
        assertEquals(7, beans.size());

        assertTrue(beans.stream().anyMatch(b -> "importingService".equals(b.beanId())));
        assertTrue(beans.stream().anyMatch(b -> "myService".equals(b.beanId())));
        assertTrue(beans.stream().anyMatch(b -> "myRepo".equals(b.beanId())));
    }

    @Test
    void detectsBeansInNestedBeansElements() {
        List<AnalysisFinding> findings = analyzer.analyze(resourcePath("nested-beans.xml"));

        List<XmlBeanInfo> beans = findings.stream()
            .filter(f -> f instanceof XmlBeanInfo)
            .map(f -> (XmlBeanInfo) f)
            .toList();

        // 1 top-level bean + 1 in dev profile + 1 in prod profile = 3
        assertEquals(3, beans.size());

        assertTrue(beans.stream().anyMatch(b -> "primaryService".equals(b.beanId())));
        assertTrue(beans.stream().anyMatch(b -> "dataSource".equals(b.beanId())));
    }

    @Test
    void resolvesClasspathStarImport() {
        List<AnalysisFinding> findings = analyzer.analyze(resourcePath("import-glob-beans.xml"));

        List<XmlBeanInfo> beans = findings.stream()
            .filter(f -> f instanceof XmlBeanInfo)
            .map(f -> (XmlBeanInfo) f)
            .toList();

        // Should contain beans from simple-beans.xml via classpath*: import
        assertTrue(beans.stream().anyMatch(b -> "myService".equals(b.beanId())));
        assertTrue(beans.stream().anyMatch(b -> "myRepo".equals(b.beanId())));
    }

    @Test
    void detectsScheduledTasksInTaskNamespace() {
        List<AnalysisFinding> findings = analyzer.analyze(resourcePath("task-scheduled-beans.xml"));

        List<XmlScheduledTaskInfo> tasks = findings.stream()
            .filter(f -> f instanceof XmlScheduledTaskInfo)
            .map(f -> (XmlScheduledTaskInfo) f)
            .toList();

        assertEquals(3, tasks.size());

        XmlScheduledTaskInfo getName = tasks.stream().filter(t -> "getName".equals(t.method())).findFirst().orElseThrow();
        assertEquals("nameService", getName.ref());
        assertEquals(Long.valueOf(10000), getName.fixedRate());
        assertEquals("fixed-rate", getName.taskType());
        assertNull(getName.cron());

        XmlScheduledTaskInfo generateReport = tasks.stream().filter(t -> "generateReport".equals(t.method())).findFirst().orElseThrow();
        assertEquals("reportService", generateReport.ref());
        assertEquals("0 0 * * * ?", generateReport.cron());
        assertEquals("cron", generateReport.taskType());

        XmlScheduledTaskInfo purgeOldData = tasks.stream().filter(t -> "purgeOldData".equals(t.method())).findFirst().orElseThrow();
        assertEquals("cleanupService", purgeOldData.ref());
        assertEquals(Long.valueOf(60000), purgeOldData.fixedDelay());
        assertEquals("fixed-delay", purgeOldData.taskType());

        // Namespace beans for <task:scheduler> and <task:executor> should still be detected
        List<XmlNamespaceBeanInfo> nsBeans = findings.stream()
            .filter(f -> f instanceof XmlNamespaceBeanInfo)
            .map(f -> (XmlNamespaceBeanInfo) f)
            .toList();
        assertTrue(nsBeans.stream().anyMatch(b -> "taskScheduler".equals(b.beanId())));
        assertTrue(nsBeans.stream().anyMatch(b -> "taskExecutor".equals(b.beanId())));
    }

    @Test
    void detectsJmsListenersInJmsNamespace() {
        List<AnalysisFinding> findings = analyzer.analyze(resourcePath("jms-listener-beans.xml"));

        List<XmlJmsListenerInfo> listeners = findings.stream()
            .filter(f -> f instanceof XmlJmsListenerInfo)
            .map(f -> (XmlJmsListenerInfo) f)
            .toList();

        assertEquals(2, listeners.size());

        XmlJmsListenerInfo order = listeners.stream().filter(l -> "onOrder".equals(l.method())).findFirst().orElseThrow();
        assertEquals("queue.order", order.destination());
        assertEquals("orderListener", order.beanName());
        assertNull(order.responseDestination());

        XmlJmsListenerInfo notification = listeners.stream().filter(l -> "onNotification".equals(l.method())).findFirst().orElseThrow();
        assertEquals("queue.notification", notification.destination());
        assertEquals("notificationListener", notification.beanName());
        assertEquals("queue.response", notification.responseDestination());
    }

    @Test
    void isSpringXmlConfigDetectsBeansNamespace() {
        assertTrue(XmlBeanAnalyzer.isSpringXmlConfig(resourcePath("simple-beans.xml")));
        assertTrue(XmlBeanAnalyzer.isSpringXmlConfig(resourcePath("namespace-beans.xml")));
        assertFalse(XmlBeanAnalyzer.isSpringXmlConfig(resourcePath("non-spring.xml")));
        assertFalse(XmlBeanAnalyzer.isSpringXmlConfig(Path.of("nonexistent.xml")));
    }
}
