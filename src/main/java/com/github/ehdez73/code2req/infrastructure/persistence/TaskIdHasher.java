package com.github.ehdez73.code2req.infrastructure.persistence;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class TaskIdHasher {

    public String hash(String filePath, String contentHash, String targetName) {
        return sha256(targetName + "|" + filePath + "|" + contentHash);
    }

    public String hash(String filePath, String contentHash, String testContentHash, String targetName) {
        return sha256(targetName + "|" + filePath + "|" + contentHash + "|" + testContentHash);
    }

    public String hash(String filePath, String contentHash, String testContentHash,
                       String modelId, String promptVersion, String targetName) {
        return sha256((targetName != null ? targetName : "")
            + "|" + filePath + "|" + contentHash + "|" + (testContentHash != null ? testContentHash : "")
            + "|" + (modelId != null ? modelId : "") + "|" + (promptVersion != null ? promptVersion : ""));
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
