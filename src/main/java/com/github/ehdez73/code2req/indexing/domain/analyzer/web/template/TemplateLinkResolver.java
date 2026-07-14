package com.github.ehdez73.code2req.indexing.domain.analyzer.web.template;

import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TemplateLinkResolver {

    public List<TemplateLinkInfo> resolve(List<TemplateFormInfo> templateForms, List<EndpointInfo> allEndpoints) {
        List<TemplateLinkInfo> links = new ArrayList<>();
        for (TemplateFormInfo form : templateForms) {
            String normalizedAction = normalizePath(form.urlPattern());
            if (normalizedAction.isEmpty()) continue;

            TemplateLinkInfo bestMatch = null;
            double bestConfidence = 0;

            for (EndpointInfo ep : allEndpoints) {
                String normalizedEpPath = ep.path();
                if (!normalizedEpPath.startsWith("/")) {
                    normalizedEpPath = "/" + normalizedEpPath;
                }

                double conf = matchConfidence(normalizedAction, normalizedEpPath, form.httpMethod(), ep.httpMethod());
                if (conf > bestConfidence) {
                    bestConfidence = conf;
                    bestMatch = new TemplateLinkInfo(
                        form.templatePath(), form.httpMethod(), form.urlPattern(),
                        ep.path(), ep.controllerName(), conf, form.linkType());
                }
            }

            if (bestMatch != null && bestConfidence >= 0.5) {
                links.add(bestMatch);
            }
        }
        return links;
    }

    private static double matchConfidence(String actionPath, String epPath, String actionMethod, String epMethod) {
        if (!actionMethod.equalsIgnoreCase(epMethod)) return 0;

        if (actionPath.equals(epPath)) return 1.0;

        String[] actionSegs = splitPath(actionPath);
        String[] epSegs = splitPath(epPath);

        if (actionSegs.length != epSegs.length) {
            if (actionPath.endsWith(epPath) || epPath.endsWith(actionPath)) return 0.5;
            return 0;
        }

        boolean allMatch = true;
        boolean hasParam = false;
        for (int i = 0; i < actionSegs.length; i++) {
            if (epSegs[i].startsWith("{") && epSegs[i].endsWith("}")) {
                hasParam = true;
            } else if (!actionSegs[i].equals(epSegs[i])) {
                allMatch = false;
                break;
            }
        }

        if (allMatch && hasParam) return 0.8;
        return 0;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) return "";
        String p = path.trim();
        if (p.startsWith("${") || p.startsWith("@{")) return "";
        if (!p.startsWith("/")) p = "/" + p;
        return p;
    }

    private static String[] splitPath(String path) {
        return path.replaceAll("^/+", "").replaceAll("/+$", "").split("/");
    }
}