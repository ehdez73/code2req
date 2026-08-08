package com.github.ehdez73.code2req.infrastructure.persistence;

import com.github.ehdez73.code2req.common.util.HashUtils;
import org.springframework.stereotype.Component;

@Component
public class TaskIdHasher {

    public String hash(String filePath, String contentHash, String targetName) {
        return HashUtils.sha256Hex(targetName + "|" + filePath + "|" + contentHash);
    }

    public String hash(String filePath, String contentHash, String testContentHash, String targetName) {
        return HashUtils.sha256Hex(targetName + "|" + filePath + "|" + contentHash + "|" + testContentHash);
    }

    public String hash(String filePath, String contentHash, String testContentHash,
                       String modelId, String promptVersion, String targetName) {
        return HashUtils.sha256Hex((targetName != null ? targetName : "")
            + "|" + filePath + "|" + contentHash + "|" + (testContentHash != null ? testContentHash : "")
            + "|" + (modelId != null ? modelId : "") + "|" + (promptVersion != null ? promptVersion : ""));
    }
}
