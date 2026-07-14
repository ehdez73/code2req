package com.github.ehdez73.code2req.indexing.domain.analyzer.web.template;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThymeleafTemplateParserTest {

    private final ThymeleafTemplateParser parser = new ThymeleafTemplateParser();

    @Test
    void supportsHtmlFiles() {
        assertTrue(parser.supports(Path.of("template.html")));
        assertTrue(parser.supports(Path.of("/path/to/template.HTML")));
    }

    @Test
    void doesNotSupportNonHtml() {
        assertFalse(parser.supports(Path.of("template.jsp")));
        assertFalse(parser.supports(Path.of("template.xml")));
    }

    @Test
    void formWithThActionAndThMethod() {
        String html = """
            <form th:action="@{/owners}" th:method="post">
                <input th:field="*{firstName}" />
                <input th:field="*{lastName}" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        TemplateFormInfo f = results.get(0);
        assertEquals("POST", f.httpMethod());
        assertEquals("/owners", f.urlPattern());
        assertFalse(f.isExpression());
        assertEquals(List.of("firstName", "lastName"), f.fieldNames());
        assertEquals("FORM", f.linkType());
    }

    @Test
    void formWithThActionAndPlainMethod() {
        String html = """
            <form th:action="@{/owners}" method="get" class="form-horizontal">
                <input th:field="*{lastName}" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        TemplateFormInfo f = results.get(0);
        assertEquals("GET", f.httpMethod());
        assertEquals("/owners", f.urlPattern());
        assertEquals(List.of("lastName"), f.fieldNames());
    }

    @Test
    void formWithThActionOnlyDefaultsToGet() {
        String html = """
            <form th:action="@{/search}">
                <input th:field="*{query}" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        TemplateFormInfo f = results.get(0);
        assertEquals("GET", f.httpMethod());
        assertEquals("/search", f.urlPattern());
    }

    @Test
    void formWithPlainMethodAndNoAction() {
        String html = """
            <form method="post" th:object="${owner}" class="form-horizontal">
                <input th:field="*{firstName}" />
                <input th:field="*{lastName}" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        TemplateFormInfo f = results.get(0);
        assertEquals("POST", f.httpMethod());
        assertEquals("", f.urlPattern());
        assertFalse(f.isExpression());
        assertEquals(List.of("firstName", "lastName"), f.fieldNames());
    }

    @Test
    void formWithNoActionAndNoMethodDefaultsToGet() {
        String html = """
            <form th:object="${visit}" class="form-horizontal">
                <input th:field="*{description}" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        TemplateFormInfo f = results.get(0);
        assertEquals("GET", f.httpMethod());
        assertEquals("", f.urlPattern());
        assertEquals(List.of("description"), f.fieldNames());
    }

    @Test
    void formWithThMethodAndThActionReversedOrder() {
        String html = """
            <form th:method="post" th:action="@{/owners}">
                <input th:field="*{name}" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        TemplateFormInfo f = results.get(0);
        assertEquals("POST", f.httpMethod());
        assertEquals("/owners", f.urlPattern());
    }

    @Test
    void formWithExpressionAction() {
        String html = """
            <form th:action="@{/owners/{id}(id=${owner.id})}" th:method="post">
                <input th:field="*{name}" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        TemplateFormInfo f = results.get(0);
        assertEquals("/owners/{id}(id=${owner.id})", f.urlPattern());
        assertTrue(f.isExpression());
    }

    @Test
    void linkWithSimplePath() {
        String html = "<a th:href=\"@{/owners/new}\">Add Owner</a>";
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        TemplateFormInfo f = results.get(0);
        assertEquals("GET", f.httpMethod());
        assertEquals("/owners/new", f.urlPattern());
        assertFalse(f.isExpression());
        assertEquals("LINK", f.linkType());
    }

    @Test
    void linkWithPreprocessorExpression() {
        String html = "<a th:href=\"@{/owners/__${owner.id}__}\">View Owner</a>";
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        TemplateFormInfo f = results.get(0);
        assertEquals("/owners/__${owner.id}__", f.urlPattern());
        assertTrue(f.isExpression());
    }

    @Test
    void linkWithStringConcatenation() {
        String html = "<a th:href=\"@{'/owners?page=' + ${i}}\">Page</a>";
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        TemplateFormInfo f = results.get(0);
        assertEquals("'/owners?page=' + ${i}", f.urlPattern());
        assertTrue(f.isExpression());
    }

    @Test
    void linkWithMultiplePreprocessors() {
        String html = "<a th:href=\"@{__${owner.id}__/pets/__${pet.id}__/edit}\">Edit Pet</a>";
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        assertEquals("__${owner.id}__/pets/__${pet.id}__/edit", results.get(0).urlPattern());
        assertTrue(results.get(0).isExpression());
    }

    @Test
    void linkWithPreprocessorInQueryParam() {
        String html = "<a th:href=\"@{'/vets.html?page=__${currentPage - 1}__'}\">Previous</a>";
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        assertEquals("'/vets.html?page=__${currentPage - 1}__'", results.get(0).urlPattern());
        assertTrue(results.get(0).isExpression());
    }

    @Test
    void externalLinkIsExcluded() {
        String html = "<a th:href=\"@{http://example.com}\">External</a>";
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertTrue(results.isEmpty());
    }

    @Test
    void anchorLinkIsExcluded() {
        String html = "<a th:href=\"@{#section}\">Anchor</a>";
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertTrue(results.isEmpty());
    }

    @Test
    void fieldWithThValueSelectExpression() {
        String html = """
            <form method="post">
                <input type="hidden" name="id" th:value="*{id}" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        assertEquals(List.of("id"), results.get(0).fieldNames());
    }

    @Test
    void fieldWithThValueVariableExpression() {
        String html = """
            <form method="post">
                <input type="hidden" name="petId" th:value="${pet.id}" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        assertEquals(List.of("pet.id"), results.get(0).fieldNames());
    }

    @Test
    void fieldWithDynamicNameViaPreprocessor() {
        String html = """
            <form method="post">
                <input th:field="*{__${name}__}" type="text" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        assertEquals(List.of("__${name}__"), results.get(0).fieldNames());
    }

    @Test
    void multipleFieldTypesInOneForm() {
        String html = """
            <form method="post" th:object="${visit}">
                <input th:field="*{description}" />
                <input type="hidden" name="petId" th:value="${pet.id}" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        assertEquals(2, results.get(0).fieldNames().size());
        assertTrue(results.get(0).fieldNames().contains("description"));
        assertTrue(results.get(0).fieldNames().contains("pet.id"));
    }

    @Test
    void multipleFormsInOneFile() {
        String html = """
            <form th:action="@{/search}" method="get">
                <input th:field="*{query}" />
            </form>
            <form th:action="@{/submit}" method="post">
                <input th:field="*{data}" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(2, results.size());
        assertEquals("GET", results.get(0).httpMethod());
        assertEquals("/search", results.get(0).urlPattern());
        assertEquals("POST", results.get(1).httpMethod());
        assertEquals("/submit", results.get(1).urlPattern());
    }

    @Test
    void multipleLinksInOneFile() {
        String html = """
            <a th:href="@{/owners}">Owners</a>
            <a th:href="@{/vets.html}">Veterinarians</a>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(f -> "LINK".equals(f.linkType())));
    }

    @Test
    void formsAndLinksMixed() {
        String html = """
            <form th:action="@{/owners}" method="get">
                <input th:field="*{lastName}" />
            </form>
            <a th:href="@{/owners/new}">Add Owner</a>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(2, results.size());
        long formCount = results.stream().filter(f -> "FORM".equals(f.linkType())).count();
        long linkCount = results.stream().filter(f -> "LINK".equals(f.linkType())).count();
        assertEquals(1, formCount);
        assertEquals(1, linkCount);
    }

    @Test
    void fragmentFileSkipsFormsButExtractsLinks() {
        String html = """
            <form th:fragment="input">
                <label>Field</label>
                <input th:field="*{name}" />
            </form>
            <a th:href="@{/owners}">Nav Link</a>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "fragments/layout.html");
        assertEquals(1, results.size());
        assertEquals("LINK", results.get(0).linkType());
        assertEquals("/owners", results.get(0).urlPattern());
    }

    @Test
    void nonFragmentFileWithFragmentsInPathIsNotExcluded() {
        String html = """
            <form th:action="@{/owners}" method="get" />
            """;
        List<TemplateFormInfo> results = parser.parse(html, "other/fragments_test.html");
        assertEquals(1, results.size());
        assertEquals("FORM", results.get(0).linkType());
    }

    @Test
    void formWithStaticResourceLinksIgnoresResourceReferences() {
        String html = """
            <link th:href="@{/resources/css/style.css}" rel="stylesheet" />
            <a th:href="@{/owners}">Owners</a>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
    }

    @Test
    void formWithEmptyBodyHasNoFields() {
        String html = """
            <form th:action="@{/empty}" method="post">
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        assertTrue(results.get(0).fieldNames().isEmpty());
    }

    @Test
    void formWithOnlyThObjectAndNoActionMethod() {
        String html = """
            <form th:object="${owner}">
                <input th:field="*{firstName}" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        assertEquals("GET", results.get(0).httpMethod());
        assertEquals("", results.get(0).urlPattern());
        assertEquals(List.of("firstName"), results.get(0).fieldNames());
    }

    @Test
    void handlesMultilineFormTag() {
        String html = """
            <form
                th:action="@{/owners}"
                method="post"
                th:object="${owner}">
                <input th:field="*{firstName}" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        assertEquals("POST", results.get(0).httpMethod());
        assertEquals("/owners", results.get(0).urlPattern());
    }

    @Test
    void formWithPlainActionAttribute() {
        String html = """
            <form action="/legacy" method="post">
                <input name="data" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        assertEquals("POST", results.get(0).httpMethod());
        assertEquals("/legacy", results.get(0).urlPattern());
        assertFalse(results.get(0).isExpression());
    }

    @Test
    void deduplicatesSameFieldFromThFieldAndThValue() {
        String html = """
            <form method="post">
                <input th:field="*{id}" />
                <input type="hidden" th:value="*{id}" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(html, "test.html");
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).fieldNames().size());
        assertEquals("id", results.get(0).fieldNames().get(0));
    }
}
