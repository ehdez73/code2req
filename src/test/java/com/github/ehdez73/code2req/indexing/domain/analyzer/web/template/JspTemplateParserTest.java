package com.github.ehdez73.code2req.indexing.domain.analyzer.web.template;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JspTemplateParserTest {

    private final JspTemplateParser parser = new JspTemplateParser();

    @Test
    void supportsJspFiles() {
        assertTrue(parser.supports(Path.of("index.jsp")));
        assertTrue(parser.supports(Path.of("/WEB-INF/views/form.JSP")));
        assertTrue(parser.supports(Path.of("templates/page.Jsp")));
    }

    @Test
    void doesNotSupportNonJsp() {
        assertFalse(parser.supports(Path.of("template.html")));
        assertFalse(parser.supports(Path.of("template.xml")));
        assertFalse(parser.supports(Path.of("template.jspx")));
    }

    @Test
    void formWithActionThenMethod() {
        String jsp = """
            <form action="/owners" method="post">
                <input name="firstName" />
                <input name="lastName" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertEquals(1, results.size());
        TemplateFormInfo f = results.get(0);
        assertEquals("POST", f.httpMethod());
        assertEquals("/owners", f.urlPattern());
        assertFalse(f.isExpression());
        assertEquals(List.of("firstName", "lastName"), f.fieldNames());
        assertEquals("FORM", f.linkType());
    }

    @Test
    void formWithMethodThenAction() {
        String jsp = """
            <form method="get" action="/search">
                <input name="q" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertEquals(1, results.size());
        TemplateFormInfo f = results.get(0);
        assertEquals("GET", f.httpMethod());
        assertEquals("/search", f.urlPattern());
        assertEquals(List.of("q"), f.fieldNames());
    }

    @Test
    void formWithActionOnlyDefaultsToGet() {
        String jsp = """
            <form action="/static-page">
                <input name="data" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertEquals(1, results.size());
        assertEquals("GET", results.get(0).httpMethod());
        assertEquals("/static-page", results.get(0).urlPattern());
    }

    @Test
    void linkWithSimpleHref() {
        String jsp = "<a href=\"/owners\">Owners</a>";
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertEquals(1, results.size());
        TemplateFormInfo f = results.get(0);
        assertEquals("GET", f.httpMethod());
        assertEquals("/owners", f.urlPattern());
        assertEquals("LINK", f.linkType());
    }

    @Test
    void externalLinkIsExcluded() {
        String jsp = "<a href=\"http://example.com\">External</a>";
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertTrue(results.stream().noneMatch(f -> "LINK".equals(f.linkType())));
    }

    @Test
    void anchorLinkIsExcluded() {
        String jsp = "<a href=\"#section\">Anchor</a>";
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertTrue(results.stream().noneMatch(f -> "LINK".equals(f.linkType())));
    }

    @Test
    void multipleFormsInOneFile() {
        String jsp = """
            <form action="/search" method="get">
                <input name="q" />
            </form>
            <form action="/submit" method="post">
                <input name="data" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertEquals(2, results.size());
        assertEquals("GET", results.get(0).httpMethod());
        assertEquals("/search", results.get(0).urlPattern());
        assertEquals("POST", results.get(1).httpMethod());
        assertEquals("/submit", results.get(1).urlPattern());
    }

    @Test
    void formsAndLinksMixed() {
        String jsp = """
            <form action="/owners" method="get">
                <input name="lastName" />
            </form>
            <a href="/owners/new">Add Owner</a>
            """;
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertEquals(2, results.size());
        long formCount = results.stream().filter(f -> "FORM".equals(f.linkType())).count();
        long linkCount = results.stream().filter(f -> "LINK".equals(f.linkType())).count();
        assertEquals(1, formCount);
        assertEquals(1, linkCount);
    }

    @Test
    void emptyContentReturnsNoResults() {
        List<TemplateFormInfo> results = parser.parse("", "empty.jsp");
        assertTrue(results.isEmpty());
    }

    @Test
    void noFormsOrLinksReturnsNoResults() {
        String jsp = "<html><body><h1>Hello</h1></body></html>";
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertTrue(results.isEmpty());
    }

    @Test
    void noFieldsInForm() {
        String jsp = """
            <form action="/empty" method="post">
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertEquals(1, results.size());
        assertTrue(results.get(0).fieldNames().isEmpty());
    }

    @Test
    void multilineFormTag() {
        String jsp = """
            <form
                action="/owners"
                method="post">
                <input name="firstName" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertEquals(1, results.size());
        assertEquals("POST", results.get(0).httpMethod());
        assertEquals("/owners", results.get(0).urlPattern());
        assertEquals(List.of("firstName"), results.get(0).fieldNames());
    }

    @Test
    void formWithNoMethodAttribute() {
        String jsp = """
            <form action="/legacy">
                <input name="field1" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertEquals(1, results.size());
        assertEquals("GET", results.get(0).httpMethod());
    }

    @Test
    void formActionWithLeadingWhitespace() {
        String jsp = """
            <form  action="/path"  method="put">
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertEquals(1, results.size());
        assertEquals("PUT", results.get(0).httpMethod());
        assertEquals("/path", results.get(0).urlPattern());
    }

    @Test
    void linkWithRelativePath() {
        String jsp = "<a href=\"relative/path\">Link</a>";
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertEquals(1, results.size());
        assertEquals("GET", results.get(0).httpMethod());
        assertEquals("relative/path", results.get(0).urlPattern());
    }

    @Test
    void multipleInputFields() {
        String jsp = """
            <form action="/register" method="post">
                <input name="username" />
                <input name="password" />
                <input name="email" />
                <input name="age" />
            </form>
            """;
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertEquals(1, results.size());
        assertEquals(List.of("username", "password", "email", "age"), results.get(0).fieldNames());
    }

    @Test
    void caseInsensitiveAttributes() {
        String jsp = "<FORM ACTION=\"/owners\" METHOD=\"POST\"><INPUT NAME=\"name\" /></FORM>";
        List<TemplateFormInfo> results = parser.parse(jsp, "index.jsp");
        assertEquals(1, results.size());
        assertEquals("POST", results.get(0).httpMethod());
        assertEquals("/owners", results.get(0).urlPattern());
        assertEquals(List.of("name"), results.get(0).fieldNames());
    }
}
