package com.github.ehdez73.code2req.indexing.application.port.output;

import com.github.ehdez73.code2req.common.domain.ProjectManifest;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.template.TemplateFormInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.template.TemplateLinkInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface IndexWriter {
    Path write(ProjectManifest manifest, List<AnalysisResult> results,
               List<TopicLink> topicLinks, List<TemplateFormInfo> templateForms,
               List<TemplateLinkInfo> templateLinks, List<FloatingLinkInfo> floatingLinks) throws IOException;
}
