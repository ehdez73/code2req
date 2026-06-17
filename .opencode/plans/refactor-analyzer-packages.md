# Package Refactoring Plan: `analyzer/`

## New Package Structure

```
analyzer/
  bean/                              ← All Spring bean detection
    ComponentInfo.java               ← moved from component/
    ComponentVisitor.java            ← moved from component/
    xml/                             ← XML bean definitions (moved from xml/)
      SpringXmlNamespaceRegistry.java
      XmlAopConfigInfo.java
      XmlBeanAnalyzer.java
      XmlBeanInfo.java
      XmlComponentScanInfo.java
      XmlNamespaceBeanInfo.java
    java/                            ← Java @Bean config (moved from component/)
      BeanMethodInfo.java
      BeanMethodVisitor.java

  web/                               ← All web-related analysis
    endpoint/                        ← Endpoint detection (moved from endpoint/ + WebXmlAnalyzer)
      EndpointDetector.java
      EndpointInfo.java
      EndpointVisitor.java
      WebXmlAnalyzer.java            ← MOVED from xml/
      detector/
        SpringEndpointDetector.java
        ServletEndpointDetector.java
    template/                        ← Template analysis (moved from template/)
      TemplateAnalyzer.java
      TemplateParser.java
      JspTemplateParser.java
      ThymeleafTemplateParser.java
      TemplateFormInfo.java
      TemplateLinkInfo.java
      TemplateLinkResolver.java
```

## Execution Steps

### Step 1: Copy files to new packages with updated `package` declarations

**`analyzer/bean/xml/`** (package: `...analyzer.bean.xml`)
- `SpringXmlNamespaceRegistry.java` — from `analyzer/xml/`
- `XmlAopConfigInfo.java` — from `analyzer/xml/`
- `XmlBeanAnalyzer.java` — from `analyzer/xml/`
- `XmlBeanInfo.java` — from `analyzer/xml/`
- `XmlComponentScanInfo.java` — from `analyzer/xml/`
- `XmlNamespaceBeanInfo.java` — from `analyzer/xml/`

**`analyzer/bean/`** (package: `...analyzer.bean`)
- `ComponentInfo.java` — from `analyzer/component/`
- `ComponentVisitor.java` — from `analyzer/component/`

**`analyzer/bean/java/`** (package: `...analyzer.bean.java`)
- `BeanMethodInfo.java` — from `analyzer/component/`
- `BeanMethodVisitor.java` — from `analyzer/component/`

**`analyzer/web/endpoint/`** (package: `...analyzer.web.endpoint`)
- `EndpointDetector.java` — from `analyzer/endpoint/`
- `EndpointInfo.java` — from `analyzer/endpoint/`
- `EndpointVisitor.java` — from `analyzer/endpoint/`
- `WebXmlAnalyzer.java` — from `analyzer/xml/`

**`analyzer/web/endpoint/detector/`** (package: `...analyzer.web.endpoint.detector`)
- `SpringEndpointDetector.java` — from `analyzer/endpoint/detector/`
- `ServletEndpointDetector.java` — from `analyzer/endpoint/detector/`

**`analyzer/web/template/`** (package: `...analyzer.web.template`)
- `TemplateAnalyzer.java` — from `analyzer/template/`
- `TemplateParser.java` — from `analyzer/template/`
- `JspTemplateParser.java` — from `analyzer/template/`
- `ThymeleafTemplateParser.java` — from `analyzer/template/`
- `TemplateFormInfo.java` — from `analyzer/template/`
- `TemplateLinkInfo.java` — from `analyzer/template/`
- `TemplateLinkResolver.java` — from `analyzer/template/`

### Step 2: Update imports in main source files

| File | Changes |
|---|---|
| `pipeline/ScanPipeline.java:10-11` | `.component.BeanMethodInfo` → `.bean.java.BeanMethodInfo`, `.component.ComponentInfo` → `.bean.ComponentInfo` |
| `pipeline/ScanPipeline.java:15` | `.endpoint.EndpointInfo` → `.web.endpoint.EndpointInfo` |
| `pipeline/ScanPipeline.java:29-32` | `.xml.Xml*` → `.bean.xml.Xml*` |
| `shell/ScanCommand.java:4` | `.endpoint.EndpointInfo` → `.web.endpoint.EndpointInfo` |
| `shell/ScanCommand.java:5-8` | `.template.*` → `.web.template.*` |
| `shell/ScanCommand.java:9` | `.xml.WebXmlAnalyzer` → `.web.endpoint.WebXmlAnalyzer` |
| `shell/ScanCommand.java:119` | FQN `com.github.ehdez73.code2req.analyzer.endpoint.EndpointInfo` → `.web.endpoint.EndpointInfo` |
| `output/IndexWriter.java:11` | `.component.BeanMethodInfo` → `.bean.java.BeanMethodInfo` |
| `output/IndexWriter.java:15` | `.component.ComponentInfo` → `.bean.ComponentInfo` |
| `output/IndexWriter.java:16` | `.endpoint.EndpointInfo` → `.web.endpoint.EndpointInfo` |
| `output/IndexWriter.java:25-26` | `.template.*` → `.web.template.*` |
| `output/IndexWriter.java:28-31` | `.xml.Xml*` → `.bean.xml.Xml*` |
| `analyzer/AnalysisResultBuilder.java:3` | `.component.ComponentInfo` → `.bean.ComponentInfo` |
| `analyzer/httpclient/FloatingLinkResolver.java:4` | `.endpoint.EndpointInfo` → `.web.endpoint.EndpointInfo` |

### Step 3: Update test files

**New test packages + move tests:**
- `test/analyzer/component/BeanMethodVisitorTest.java` → `test/analyzer/bean/java/BeanMethodVisitorTest.java` (package: `.bean.java`)
- `test/analyzer/component/ComponentVisitorTest.java` → `test/analyzer/bean/ComponentVisitorTest.java` (package: `.bean`)
- `test/analyzer/xml/XmlBeanAnalyzerTest.java` → `test/analyzer/bean/xml/XmlBeanAnalyzerTest.java` (package: `.bean.xml`)
- `test/analyzer/endpoint/EndpointVisitorTest.java` → `test/analyzer/web/endpoint/EndpointVisitorTest.java` (package: `.web.endpoint`)

**Test import updates:**

| File | Changes |
|---|---|
| `ScanCommandTest.java:5` | `.component.ComponentInfo` → `.bean.ComponentInfo` |
| `ScanCommandTest.java:7-8` | `.endpoint.*` → `.web.endpoint.*` |
| `ScanCommandTest.java:11` | `.xml.WebXmlAnalyzer` → `.web.endpoint.WebXmlAnalyzer` |
| `ScanCommandTest.java:19-20` | `.template.*` → `.web.template.*` |
| `ScanCommandTest.java:76` | FQN `.component.ComponentVisitor` → `.bean.ComponentVisitor` |
| `ScanCommandTest.java:77` | FQN `.endpoint.EndpointVisitor` → `.web.endpoint.EndpointVisitor` |
| `ScanCommandTest.java:82` | FQN `.component.BeanMethodVisitor` → `.bean.java.BeanMethodVisitor` |
| `ScanCommandTest.java:100` | FQN `.template.JspTemplateParser` → `.web.template.JspTemplateParser`, `.template.ThymeleafTemplateParser` → `.web.template.ThymeleafTemplateParser` |
| `ResumeCommandTest.java:4-5` | `.component.*` → `.bean.*` / `.bean.java.*` |
| `ResumeCommandTest.java:7-9` | `.endpoint.*` → `.web.endpoint.*` |
| `ScanPipelineTest.java:5` | `.component.ComponentVisitor` → `.bean.ComponentVisitor` |
| `JavaAstAnalyzerTest.java:3-4` | `.component.*` → `.bean.*` |
| `JavaAstAnalyzerTest.java:5-8` | `.endpoint.*` → `.web.endpoint.*` |
| `EndpointVisitorTest.java:6` | `.component.ComponentInfo` → `.bean.ComponentInfo` |
| `EndpointVisitorTest.java:7` | `.endpoint.detector.SpringEndpointDetector` → `.web.endpoint.detector.SpringEndpointDetector` |
| `IndexWriterTest.java:9-10` | `.component.*` → `.bean.*` / `.bean.java.*` |
| `IndexWriterTest.java:11` | `.endpoint.EndpointInfo` → `.web.endpoint.EndpointInfo` |
| `FloatingLinkResolverTest.java:5` | `.endpoint.EndpointInfo` → `.web.endpoint.EndpointInfo` |

### Step 4: Delete old directories

After copying all files and verifying the build:
```
rm -rf src/main/java/com/github/ehdez73/code2req/analyzer/xml/
rm -rf src/main/java/com/github/ehdez73/code2req/analyzer/component/
rm -rf src/main/java/com/github/ehdez73/code2req/analyzer/endpoint/
rm -rf src/main/java/com/github/ehdez73/code2req/analyzer/template/
rm -rf src/test/java/com/github/ehdez73/code2req/analyzer/component/
rm -rf src/test/java/com/github/ehdez73/code2req/analyzer/xml/
rm -rf src/test/java/com/github/ehdez73/code2req/analyzer/endpoint/
```

### Step 5: Build and test

```bash
./mvnw clean compile  # verify compilation
./mvnw test           # run all tests
```
