package com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class XmlBeanAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(XmlBeanAnalyzer.class);
    private static final String BEANS_NAMESPACE = "http://www.springframework.org/schema/beans";
    private static final String TASK_NS = "http://www.springframework.org/schema/task";
    private static final String JMS_NS = "http://www.springframework.org/schema/jms";

    public List<AnalysisFinding> analyze(Path filePath) {
        Set<Path> visited = new HashSet<>();
        List<AnalysisFinding> findings = new ArrayList<>();
        collectFindings(filePath, visited, findings);
        return findings;
    }

    public List<AnalysisFinding> analyze(Path filePath, Path sourceRoot) {
        Set<Path> visited = new HashSet<>();
        List<AnalysisFinding> findings = new ArrayList<>();
        collectFindings(filePath, sourceRoot, visited, findings);
        return findings;
    }

    private void collectFindings(Path filePath, Set<Path> visited, List<AnalysisFinding> findings) {
        collectFindings(filePath, null, visited, findings);
    }

    private void collectFindings(Path filePath, Path sourceRoot, Set<Path> visited, List<AnalysisFinding> findings) {
        Path normalized = filePath.toAbsolutePath().normalize();
        if (!visited.add(normalized)) {
            return;
        }

        if (!Files.exists(normalized)) {
            log.warn("XML file not found: {}", normalized);
            return;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(normalized.toFile());
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            String rootNs = root.getNamespaceURI();
            if (!BEANS_NAMESPACE.equals(rootNs)) {
                return;
            }

            Path parentDir = normalized.getParent();
            processChildElements(root.getChildNodes(), normalized, parentDir, sourceRoot, visited, findings);
        } catch (Exception e) {
            log.warn("Failed to parse XML {}: {}", normalized, e.getMessage());
        }
    }

    private void processChildElements(NodeList children, Path currentFile, Path parentDir, Path sourceRoot,
                                       Set<Path> visited, List<AnalysisFinding> findings) {
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) child;
            String ns = el.getNamespaceURI();
            String localName = el.getLocalName();

            if (BEANS_NAMESPACE.equals(ns) && "bean".equals(localName)) {
                findings.add(parseBeanElement(el, currentFile));
            } else if (BEANS_NAMESPACE.equals(ns) && "import".equals(localName)) {
                resolveImport(el, parentDir, sourceRoot, visited, findings);
            } else if (BEANS_NAMESPACE.equals(ns) && "alias".equals(localName)) {
                findings.add(parseAliasElement(el, currentFile));
            } else if (BEANS_NAMESPACE.equals(ns) && "beans".equals(localName)) {
                processChildElements(el.getChildNodes(), currentFile, parentDir, sourceRoot, visited, findings);
            } else if (TASK_NS.equals(ns) && "scheduled".equals(localName)) {
                findings.add(parseTaskScheduled(el, currentFile));
            } else if (JMS_NS.equals(ns) && "listener".equals(localName)) {
                findings.add(parseJmsListener(el, currentFile));
            } else if (SpringXmlNamespaceRegistry.isKnownNamespace(ns)) {
                findings.add(parseNamespaceElement(el, ns, localName, currentFile));
                processChildElements(el.getChildNodes(), currentFile, parentDir, sourceRoot, visited, findings);
            }
        }
    }

    private XmlBeanInfo parseBeanElement(Element el, Path filePath) {
        String id = el.getAttribute("id");
        if (id.isEmpty()) {
            id = el.getAttribute("name");
        }
        String clazz = el.getAttribute("class");
        if (clazz.isEmpty()) {
            clazz = null;
        }
        String scope = el.getAttribute("scope");
        if (scope.isEmpty()) {
            scope = "singleton";
        }
        String factoryMethod = el.getAttribute("factory-method");
        if (factoryMethod.isEmpty()) {
            factoryMethod = null;
        }
        return new XmlBeanInfo(id, clazz, scope, factoryMethod, filePath != null ? filePath.toString() : "", el.getUserData("lineNumber") != null ? (int) el.getUserData("lineNumber") : 0);
    }

    private XmlBeanInfo parseAliasElement(Element el, Path filePath) {
        String alias = el.getAttribute("alias");
        String name = el.getAttribute("name");
        return new XmlBeanInfo(alias, name, null, null, filePath != null ? filePath.toString() : "", 0);
    }

    private AnalysisFinding parseNamespaceElement(Element el, String ns, String localName, Path filePath) {
        String id = el.getAttribute("id");
        if (id.isEmpty()) {
            id = el.getAttribute("bean");
        }
        String resolvedType = SpringXmlNamespaceRegistry.resolveType(ns, localName);

        if (SpringXmlNamespaceRegistry.isComponentScanNamespace(ns) && "component-scan".equals(localName)) {
            String basePackage = el.getAttribute("base-package");
            return new XmlComponentScanInfo(basePackage.isEmpty() ? el.getTextContent() : basePackage, filePath != null ? filePath.toString() : "");
        }

        if (SpringXmlNamespaceRegistry.isAopOrTxNamespace(ns)) {
            return new XmlAopConfigInfo(ns + ":" + localName, filePath != null ? filePath.toString() : "", 0);
        }

        return new XmlNamespaceBeanInfo(id, ns, localName, resolvedType, filePath != null ? filePath.toString() : "", 0);
    }

    private XmlScheduledTaskInfo parseTaskScheduled(Element el, Path currentFile) {
        String ref = el.getAttribute("ref");
        String method = el.getAttribute("method");
        String cron = el.getAttribute("cron");
        String fixedRateStr = el.getAttribute("fixed-rate");
        String fixedDelayStr = el.getAttribute("fixed-delay");
        Long fixedRate = fixedRateStr.isEmpty() ? null : Long.parseLong(fixedRateStr);
        Long fixedDelay = fixedDelayStr.isEmpty() ? null : Long.parseLong(fixedDelayStr);
        String taskType = !cron.isEmpty() ? "cron" : fixedRate != null ? "fixed-rate" : fixedDelay != null ? "fixed-delay" : "unknown";
        return new XmlScheduledTaskInfo(ref, method,
            cron.isEmpty() ? null : cron,
            fixedRate, fixedDelay, taskType,
            currentFile != null ? currentFile.toString() : "", 0);
    }

    private XmlJmsListenerInfo parseJmsListener(Element el, Path currentFile) {
        String destination = el.getAttribute("destination");
        String ref = el.getAttribute("ref");
        String method = el.getAttribute("method");
        String responseDestination = el.getAttribute("response-destination");
        return new XmlJmsListenerInfo(destination, ref, method,
            responseDestination.isEmpty() ? null : responseDestination,
            currentFile != null ? currentFile.toString() : "", 0);
    }

    private void resolveImport(Element el, Path parentDir, Path sourceRoot,
                                Set<Path> visited, List<AnalysisFinding> findings) {
        String resource = el.getAttribute("resource");
        if (resource.isEmpty()) {
            return;
        }

        List<Path> resolvedPaths = ResourcePathResolver.resolve(resource, sourceRoot, parentDir);
        for (Path resolved : resolvedPaths) {
            collectFindings(resolved, sourceRoot, visited, findings);
        }
    }

    public static boolean isSpringXmlConfig(Path filePath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(filePath.toFile());
            doc.getDocumentElement().normalize();
            Element root = doc.getDocumentElement();
            return BEANS_NAMESPACE.equals(root.getNamespaceURI());
        } catch (Exception e) {
            return false;
        }
    }
}
