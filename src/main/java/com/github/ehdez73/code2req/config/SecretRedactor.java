package com.github.ehdez73.code2req.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SecretRedactor {

    private static final Logger log = LoggerFactory.getLogger(SecretRedactor.class);

    private static final List<SecretRule> RULES = List.of(
        new SecretRule(Pattern.compile("(?i)(password\\s*=\\s*)\"[^\"]*\""), "$1\"[REDACTED:password]\"", "password"),
        new SecretRule(Pattern.compile("(?i)(passwd\\s*=\\s*)\"[^\"]*\""), "$1\"[REDACTED:password]\"", "password"),
        new SecretRule(Pattern.compile("(?i)(\\bpass\\s*=\\s*)\"[^\"]*\""), "$1\"[REDACTED:password]\"", "password"),
        new SecretRule(Pattern.compile("(?i)(api[_]?key\\s*=\\s*)\"[^\"]*\""), "$1\"[REDACTED:api_key]\"", "api_key"),
        new SecretRule(Pattern.compile("(?i)(token\\s*=\\s*)\"[^\"]*\""), "$1\"[REDACTED:token]\"", "token"),
        new SecretRule(Pattern.compile("(?i)(secret\\s*=\\s*)\"[^\"]*\""), "$1\"[REDACTED:secret]\"", "secret"),
        new SecretRule(Pattern.compile("(?i)(jdbc:\\w+://)[^:@]+(:[^@]+)?@"), "$1[REDACTED:credentials]@", "connection_string")
    );

    public String redact(String content) {
        return redactWithResult(content).redactedContent();
    }

    public RedactionResult redactWithResult(String content) {
        if (content == null || content.isEmpty()) {
            return new RedactionResult(content, Map.of());
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        String result = content;

        for (SecretRule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(result);
            if (!matcher.find()) {
                continue;
            }
            matcher.reset();
            StringBuffer sb = new StringBuffer();
            int count = 0;
            while (matcher.find()) {
                matcher.appendReplacement(sb, rule.replacement());
                count++;
            }
            matcher.appendTail(sb);
            result = sb.toString();
            if (count > 0) {
                counts.merge(rule.type(), count, Integer::sum);
                log.debug("Redacted {} occurrence(s) of type '{}'", count, rule.type());
            }
        }

        return new RedactionResult(result, counts);
    }

    private record SecretRule(Pattern pattern, String replacement, String type) {}
}
