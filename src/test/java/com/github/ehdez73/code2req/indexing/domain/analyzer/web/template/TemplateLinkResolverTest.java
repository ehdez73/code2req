package com.github.ehdez73.code2req.indexing.domain.analyzer.web.template;

import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateLinkResolverTest {

    private final TemplateLinkResolver resolver = new TemplateLinkResolver();

    @Test
    void emptyListsReturnsNoLinks() {
        assertTrue(resolver.resolve(List.of(), List.of()).isEmpty());
    }

    @Test
    void noEndpointsReturnsNoLinks() {
        var forms = List.of(new TemplateFormInfo("GET", "/owners", false, List.of(), "FORM", "index.jsp"));
        assertTrue(resolver.resolve(forms, List.of()).isEmpty());
    }

    @Test
    void exactMatchReturnsConfidenceOne() {
        var form = new TemplateFormInfo("GET", "/owners", false, List.of(), "FORM", "index.jsp");
        var endpoint = endpoint("GET", "/owners");
        List<TemplateLinkInfo> links = resolver.resolve(List.of(form), List.of(endpoint));
        assertEquals(1, links.size());
        assertEquals(1.0, links.get(0).confidence(), 0.001);
        assertEquals("index.jsp", links.get(0).templatePath());
        assertEquals("/owners", links.get(0).matchedEndpointPath());
    }

    @Test
    void pathParameterizedMatchReturnsConfidenceEight() {
        var form = new TemplateFormInfo("GET", "/owners/5", false, List.of(), "FORM", "index.jsp");
        var endpoint = endpoint("GET", "/owners/{ownerId}");
        List<TemplateLinkInfo> links = resolver.resolve(List.of(form), List.of(endpoint));
        assertEquals(1, links.size());
        assertEquals(0.8, links.get(0).confidence(), 0.001);
    }

    @Test
    void methodMismatchReturnsNoMatch() {
        var form = new TemplateFormInfo("POST", "/owners", false, List.of(), "FORM", "index.jsp");
        var endpoint = endpoint("GET", "/owners");
        assertTrue(resolver.resolve(List.of(form), List.of(endpoint)).isEmpty());
    }

    @Test
    void belowConfidenceThresholdIsFiltered() {
        var form = new TemplateFormInfo("GET", "/owners/5/details", false, List.of(), "FORM", "index.jsp");
        var endpoint = endpoint("GET", "/owners");
        assertTrue(resolver.resolve(List.of(form), List.of(endpoint)).isEmpty());
    }

    @Test
    void actionEndsWithEndpointPathReturnsHalfConfidence() {
        var form = new TemplateFormInfo("GET", "/api/v1/owners", false, List.of(), "FORM", "index.jsp");
        var endpoint = endpoint("GET", "/owners");
        List<TemplateLinkInfo> links = resolver.resolve(List.of(form), List.of(endpoint));
        assertEquals(1, links.size());
        assertEquals(0.5, links.get(0).confidence(), 0.001);
    }

    @Test
    void endpointEndsWithActionPathReturnsHalfConfidence() {
        var form = new TemplateFormInfo("GET", "/owners", false, List.of(), "FORM", "index.jsp");
        var endpoint = endpoint("GET", "/api/v1/owners");
        List<TemplateLinkInfo> links = resolver.resolve(List.of(form), List.of(endpoint));
        assertEquals(1, links.size());
        assertEquals(0.5, links.get(0).confidence(), 0.001);
    }

    @Test
    void bestMatchSelectedWhenMultipleEndpointsMatch() {
        var form = new TemplateFormInfo("GET", "/owners/5", false, List.of(), "FORM", "index.jsp");
        var exactEndpoint = endpoint("GET", "/owners/5");
        var paramEndpoint = endpoint("GET", "/owners/{id}");
        var unmatchedEndpoint = endpoint("GET", "/pets");
        List<TemplateLinkInfo> links = resolver.resolve(
            List.of(form),
            List.of(unmatchedEndpoint, exactEndpoint, paramEndpoint));
        assertEquals(1, links.size());
        assertEquals(1.0, links.get(0).confidence(), 0.001);
        assertEquals("/owners/5", links.get(0).matchedEndpointPath());
    }

    @Test
    void blankActionPathReturnsEmpty() {
        var form = new TemplateFormInfo("GET", "", false, List.of(), "FORM", "index.jsp");
        assertTrue(resolver.resolve(List.of(form), List.of(endpoint("GET", "/owners"))).isEmpty());
    }

    @Test
    void nullActionPathReturnsEmpty() {
        var form = new TemplateFormInfo("GET", null, false, List.of(), "FORM", "index.jsp");
        assertTrue(resolver.resolve(List.of(form), List.of(endpoint("GET", "/owners"))).isEmpty());
    }

    @Test
    void expressionPathIsSkipped() {
        var form = new TemplateFormInfo("GET", "${dynamic.path}", true, List.of(), "FORM", "index.jsp");
        assertTrue(resolver.resolve(List.of(form), List.of(endpoint("GET", "/owners"))).isEmpty());
    }

    @Test
    void thymeleafExpressionPathIsSkipped() {
        var form = new TemplateFormInfo("GET", "@{/owners}", true, List.of(), "FORM", "index.jsp");
        assertTrue(resolver.resolve(List.of(form), List.of(endpoint("GET", "/owners"))).isEmpty());
    }

    @Test
    void endpointPathWithoutLeadingSlashIsNormalized() {
        var form = new TemplateFormInfo("GET", "/owners", false, List.of(), "FORM", "index.jsp");
        var endpoint = new EndpointInfo("GET", "owners", "OwnerController", "list",
            List.of(), List.of(), "OwnerController.java", false, "", List.of());
        List<TemplateLinkInfo> links = resolver.resolve(List.of(form), List.of(endpoint));
        assertEquals(1, links.size());
        assertEquals(1.0, links.get(0).confidence(), 0.001);
    }

    @Test
    void formActionWithoutLeadingSlashIsNormalized() {
        var form = new TemplateFormInfo("GET", "owners", false, List.of(), "FORM", "index.jsp");
        var endpoint = endpoint("GET", "/owners");
        List<TemplateLinkInfo> links = resolver.resolve(List.of(form), List.of(endpoint));
        assertEquals(1, links.size());
        assertEquals(1.0, links.get(0).confidence(), 0.001);
    }

    @Test
    void multipleFormsEachResolvedSeparately() {
        var form1 = new TemplateFormInfo("GET", "/owners", false, List.of(), "FORM", "index.jsp");
        var form2 = new TemplateFormInfo("POST", "/owners", false, List.of(), "FORM", "index.jsp");
        var ep1 = endpoint("GET", "/owners");
        var ep2 = endpoint("POST", "/owners");
        List<TemplateLinkInfo> links = resolver.resolve(List.of(form1, form2), List.of(ep1, ep2));
        assertEquals(2, links.size());
    }

    @Test
    void formActionWithTrailingSlash() {
        var form = new TemplateFormInfo("GET", "/owners/", false, List.of(), "FORM", "index.jsp");
        var endpoint = endpoint("GET", "/owners");
        assertTrue(resolver.resolve(List.of(form), List.of(endpoint)).isEmpty());
    }

    private static EndpointInfo endpoint(String method, String path) {
        return new EndpointInfo(method, path, "TestController", "handle",
            List.of(), List.of(), "TestController.java", false, "", List.of());
    }
}
