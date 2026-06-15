package com.github.ehdez73.code2req.analyzer.db.detector;

import java.util.List;

import org.springframework.stereotype.Component;

import com.github.ehdez73.code2req.analyzer.db.DbAccessDetector;
import com.github.ehdez73.code2req.analyzer.db.DbAccessHelper;
import com.github.ehdez73.code2req.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.analyzer.db.DbAccessType;
import com.github.javaparser.ast.body.MethodDeclaration;

@Component
public class ProcedureDetector implements DbAccessDetector {

    @Override
    public void detect(List<DbAccessInfo> result, MethodDeclaration method,
                       String className, String filePath) {
        method.getAnnotationByName("Procedure").ifPresent(ann -> {
            String procedureName = DbAccessHelper.extractProcedureName(ann);
            result.add(new DbAccessInfo(
                DbAccessType.PROCEDURE.name(), "", "", procedureName,
                method.getNameAsString(), className, filePath, "", false));
        });
    }
}
