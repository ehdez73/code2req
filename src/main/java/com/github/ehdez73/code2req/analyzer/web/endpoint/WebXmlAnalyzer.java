package com.github.ehdez73.code2req.analyzer.web.endpoint;

import com.github.ehdez73.code2req.analyzer.web.endpoint.EndpointInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebXmlAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(WebXmlAnalyzer.class);

    private static final List<String> WEB_APP_NAMESPACES = List.of(
        "http://xmlns.jcp.org/xml/ns/javaee",
        "http://java.sun.com/xml/ns/javaee",
        "http://java.sun.com/xml/ns/j2ee"
    );

    public List<EndpointInfo> analyze(Path filePath) {
        if (!Files.exists(filePath)) {
            log.warn("web.xml not found: {}", filePath);
            return List.of();
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(filePath.toFile());
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            if (!"web-app".equals(root.getLocalName())) {
                return List.of();
            }

            String ns = root.getNamespaceURI();
            if (ns != null && !WEB_APP_NAMESPACES.contains(ns)) {
                return List.of();
            }

            Map<String, String> servletClasses = extractServletClasses(doc, ns);
            Map<String, List<String>> servletMappings = extractServletMappings(doc, ns);

            List<EndpointInfo> endpoints = new ArrayList<>();
            for (var entry : servletMappings.entrySet()) {
                String servletName = entry.getKey();
                String servletClass = servletClasses.get(servletName);
                if (servletClass == null) continue;

                for (String urlPattern : entry.getValue()) {
                    String path = urlPattern;
                    if (!path.startsWith("/")) {
                        path = "/" + path;
                    }
                    endpoints.add(new EndpointInfo("", path, servletClass,
                        List.of(), List.of(), filePath.toString(), false, ""));
                }
            }

            return endpoints;
        } catch (Exception e) {
            log.warn("Failed to parse web.xml {}: {}", filePath, e.getMessage());
            return List.of();
        }
    }

    private static Map<String, String> extractServletClasses(Document doc, String ns) {
        Map<String, String> map = new HashMap<>();
        NodeList servlets = ns != null
            ? doc.getElementsByTagNameNS(ns, "servlet")
            : doc.getElementsByTagName("servlet");

        for (int i = 0; i < servlets.getLength(); i++) {
            Element servletEl = (Element) servlets.item(i);
            String name = getChildText(servletEl, "servlet-name", ns);
            String clazz = getChildText(servletEl, "servlet-class", ns);
            if (name != null && clazz != null && !clazz.isEmpty()) {
                map.put(name, clazz);
            }
        }
        return map;
    }

    private static Map<String, List<String>> extractServletMappings(Document doc, String ns) {
        Map<String, List<String>> map = new HashMap<>();
        NodeList mappings = ns != null
            ? doc.getElementsByTagNameNS(ns, "servlet-mapping")
            : doc.getElementsByTagName("servlet-mapping");

        for (int i = 0; i < mappings.getLength(); i++) {
            Element mappingEl = (Element) mappings.item(i);
            String name = getChildText(mappingEl, "servlet-name", ns);
            if (name == null) continue;

            List<String> patterns = new ArrayList<>();
            NodeList urlPatterns = getChildElementsByTagName(mappingEl, "url-pattern", ns);
            for (int j = 0; j < urlPatterns.getLength(); j++) {
                String text = urlPatterns.item(j).getTextContent();
                if (text != null && !text.trim().isEmpty()) {
                    patterns.add(text.trim());
                }
            }

            if (!patterns.isEmpty()) {
                map.merge(name, patterns, (a, b) -> {
                    var merged = new ArrayList<>(a);
                    merged.addAll(b);
                    return merged;
                });
            }
        }
        return map;
    }

    private static String getChildText(Element parent, String tagName, String ns) {
        NodeList children = ns != null
            ? parent.getElementsByTagNameNS(ns, tagName)
            : parent.getElementsByTagName(tagName);
        if (children.getLength() > 0) {
            String text = children.item(0).getTextContent();
            return text != null ? text.trim() : null;
        }
        return null;
    }

    private static NodeList getChildElementsByTagName(Element parent, String tagName, String ns) {
        return ns != null
            ? parent.getElementsByTagNameNS(ns, tagName)
            : parent.getElementsByTagName(tagName);
    }
}
