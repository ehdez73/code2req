package com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FloatingLinkResolver {

    public List<FloatingLinkInfo> resolve(List<AnalysisResult> results) {
        List<OutboundHttpCallInfo> calls = new ArrayList<>();
        for (AnalysisResult result : results) {
            calls.addAll(result.findings(OutboundHttpCallInfo.class));
        }

        List<EndpointInfo> endpoints = new ArrayList<>();
        for (AnalysisResult result : results) {
            endpoints.addAll(result.findings(EndpointInfo.class));
        }

        List<FloatingLinkInfo> links = new ArrayList<>();
        for (OutboundHttpCallInfo call : calls) {
            links.add(resolveSingle(call, endpoints));
        }
        return links;
    }

    static FloatingLinkInfo resolveSingle(OutboundHttpCallInfo call, List<EndpointInfo> endpoints) {
        String callUrl = call.urlPattern();
        String httpMethod = call.method();

        EndpointInfo bestMatch = null;
        double bestConfidence = 0.0;

        for (EndpointInfo ep : endpoints) {
            if (!ep.httpMethod().equalsIgnoreCase(httpMethod)) continue;

            double conf = matchConfidence(callUrl, ep.path());
            if (conf > bestConfidence) {
                bestConfidence = conf;
                bestMatch = ep;
                if (conf == 1.0) break;
            }
        }

        if (bestMatch != null) {
            return new FloatingLinkInfo(
                httpMethod, callUrl, call.isExpression(), call.clientType(),
                call.filePath(), call.encapsulatedIn(),
                bestMatch.path(), bestConfidence,
                FloatingLinkInfo.STATUS_RESOLVED
            );
        }

        return new FloatingLinkInfo(
            httpMethod, callUrl, call.isExpression(), call.clientType(),
            call.filePath(), call.encapsulatedIn(),
            null, 0.0,
            FloatingLinkInfo.STATUS_PENDING
        );
    }

    static double matchConfidence(String callUrl, String endpointPath) {
        if (callUrl.equals(endpointPath)) {
            return 1.0;
        }

        String[] callSegments = callUrl.split("/");
        String[] epSegments = endpointPath.split("/");

        if (callSegments.length != epSegments.length) {
            int prefixMatch = 0;
            int minLen = Math.min(callSegments.length, epSegments.length);
            for (int i = 0; i < minLen; i++) {
                if (callSegments[i].equals(epSegments[i])) {
                    prefixMatch++;
                } else if (epSegments[i].matches("\\{[^}]+\\}")) {
                    prefixMatch++;
                } else {
                    break;
                }
            }
            if (prefixMatch == minLen && minLen > 1) {
                return 0.4;
            }
            return 0.0;
        }

        int matchingSegments = 0;
        int pathVarSegments = 0;
        int expressionSegments = 0;
        for (int i = 0; i < callSegments.length; i++) {
            if (callSegments[i].equals(epSegments[i])) {
                matchingSegments++;
            } else if (epSegments[i].matches("\\{[^}]+\\}")) {
                pathVarSegments++;
            } else if (epSegments[i].contains("${") || callSegments[i].contains("${")) {
                expressionSegments++;
            } else {
                return 0.0;
            }
        }

        if (matchingSegments == callSegments.length) {
            return 1.0;
        }

        double baseScore = (double) matchingSegments / callSegments.length;
        double varScore = pathVarSegments * 0.8 / callSegments.length;
        double exprScore = expressionSegments * 0.6 / callSegments.length;
        double total = baseScore + varScore + exprScore;

        if (total >= 0.9) return 1.0;
        if (total >= 0.7) return 0.8;
        if (total >= 0.5) return 0.6;
        if (total >= 0.3) return 0.4;
        return 0.0;
    }
}
