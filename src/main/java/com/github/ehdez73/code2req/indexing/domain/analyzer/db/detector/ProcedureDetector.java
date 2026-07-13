package com.github.ehdez73.code2req.indexing.domain.analyzer.db.detector;

import java.util.List;

import org.springframework.stereotype.Component;

import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessDetector;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessHelper;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessType;
import com.github.javaparser.ast.body.MethodDeclaration;

@Component
public class ProcedureDetector implements DbAccessDetector {

    @Override
    public void detect(List<DbAccessInfo> result, MethodDeclaration method,
                       String className, String filePath) {
        int startLine = method.getBegin().map(r -> r.line).orElse(0);
        int endLine = method.getEnd().map(r -> r.line).orElse(0);
        method.getAnnotationByName("Procedure").ifPresent(ann -> {
            String procedureName = DbAccessHelper.extractProcedureName(ann);
            result.add(new DbAccessInfo(
                DbAccessType.PROCEDURE.name(), "", "", procedureName,
                method.getNameAsString(), className, filePath, "", false, startLine, endLine, 0));
        });
    }
}
