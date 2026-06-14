package com.github.ehdez73.code2req.analyzer.xml;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class SpringXmlNamespaceRegistry {

    private static final Map<String, Map<String, String>> NAMESPACE_TYPES;

    static {
        Map<String, Map<String, String>> ns = new HashMap<>();

        Map<String, String> util = new HashMap<>();
        util.put("constant", null);
        util.put("property-path", null);
        util.put("list", "java.util.List");
        util.put("set", "java.util.Set");
        util.put("map", "java.util.Map");
        util.put("properties", "java.util.Properties");
        ns.put("http://www.springframework.org/schema/util", util);

        Map<String, String> jdbc = new HashMap<>();
        jdbc.put("embedded-database", "javax.sql.DataSource");
        jdbc.put("initialize-database", null);
        ns.put("http://www.springframework.org/schema/jdbc", jdbc);

        Map<String, String> task = new HashMap<>();
        task.put("scheduler", "org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler");
        task.put("executor", "org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor");
        task.put("scheduled-tasks", null);
        ns.put("http://www.springframework.org/schema/task", task);

        Map<String, String> cache = new HashMap<>();
        cache.put("annotation-driven", null);
        cache.put("cache-manager", null);
        ns.put("http://www.springframework.org/schema/cache", cache);

        Map<String, String> tx = new HashMap<>();
        tx.put("annotation-driven", null);
        tx.put("advice", null);
        tx.put("jta-transaction-manager", "org.springframework.transaction.jta.JtaTransactionManager");
        ns.put("http://www.springframework.org/schema/tx", tx);

        Map<String, String> aop = new HashMap<>();
        aop.put("config", null);
        aop.put("aspectj-autoproxy", null);
        ns.put("http://www.springframework.org/schema/aop", aop);

        Map<String, String> context = new HashMap<>();
        context.put("component-scan", null);
        context.put("annotation-config", null);
        context.put("property-placeholder", null);
        context.put("property-override", null);
        context.put("load-time-weaver", null);
        context.put("spring-configured", null);
        context.put("mbean-export", null);
        context.put("mbean-server", null);
        ns.put("http://www.springframework.org/schema/context", context);

        Map<String, String> lang = new HashMap<>();
        lang.put("groovy", null);
        lang.put("bsh", null);
        lang.put("std", null);
        ns.put("http://www.springframework.org/schema/lang", lang);

        Map<String, String> jee = new HashMap<>();
        jee.put("jndi-lookup", null);
        jee.put("local-slsb", null);
        jee.put("remote-slsb", null);
        ns.put("http://www.springframework.org/schema/jee", jee);

        Map<String, String> jms = new HashMap<>();
        jms.put("listener-container", null);
        jms.put("jca-listener-container", null);
        jms.put("annotation-driven", null);
        ns.put("http://www.springframework.org/schema/jms", jms);

        Map<String, String> mvc = new HashMap<>();
        mvc.put("annotation-driven", null);
        mvc.put("resources", null);
        mvc.put("default-servlet-handler", null);
        mvc.put("interceptors", null);
        mvc.put("view-controller", null);
        mvc.put("view-resolvers", null);
        mvc.put("freemarker-configurer", "org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer");
        ns.put("http://www.springframework.org/schema/mvc", mvc);

        Map<String, String> oxm = new HashMap<>();
        oxm.put("jaxb2-marshaller", "org.springframework.oxm.jaxb.Jaxb2Marshaller");
        ns.put("http://www.springframework.org/schema/oxm", oxm);

        NAMESPACE_TYPES = Collections.unmodifiableMap(ns);
    }

    private SpringXmlNamespaceRegistry() {
    }

    public static String resolveType(String namespaceUri, String localName) {
        Map<String, String> elements = NAMESPACE_TYPES.get(namespaceUri);
        if (elements == null) {
            return null;
        }
        return elements.get(localName);
    }

    public static boolean isKnownNamespace(String namespaceUri) {
        return NAMESPACE_TYPES.containsKey(namespaceUri);
    }

    public static boolean isComponentScanNamespace(String namespaceUri) {
        return "http://www.springframework.org/schema/context".equals(namespaceUri);
    }

    public static boolean isAopOrTxNamespace(String namespaceUri) {
        return "http://www.springframework.org/schema/aop".equals(namespaceUri)
            || "http://www.springframework.org/schema/tx".equals(namespaceUri)
            || "http://www.springframework.org/schema/cache".equals(namespaceUri);
    }
}
