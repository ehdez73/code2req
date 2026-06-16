# US032 — Developer traces outbound HTTP calls

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F012 — Outbound HTTP Client Detection
**Priority:** should | **Estimate:** 5 SP
**Depends on:** US006 | **Blocks:** US013

> As a **Developer**, I want **the tool to capture all outbound HTTP client calls**, so that **I can map inter-service dependencies and identify external API consumers**.

### Acceptance Criteria

- [x] RestTemplate.exchange(), getForObject(), postForObject(), put(), delete() are detected
- [x] WebClient fluent builder chains are followed to extract method and URL
- [x] @FeignClient interfaces are detected with their method-level mappings
- [x] RestClient (Spring 6.1) fluent chains are detected (.get().uri(), .post().uri(), etc.)
- [x] @HttpExchange / @GetExchange / @PostExchange interfaces are detected
- [x] java.net.http.HttpClient.send()/sendAsync() with HttpRequest are detected
- [x] Legacy HttpURLConnection via openConnection/setRequestMethod/connect is detected
- [x] Apache HttpClient via HttpGet/HttpPost/HttpPut/HttpDelete is detected
- [x] OkHttp via OkHttpClient.newCall() with Request.Builder is detected
- [x] URL literals are captured as-is
- [x] SpEL expressions and environment variable references are captured as patterns with isExpression=true
- [x] Each detection is registered as a floating_link in the SQLite store
- [x] FloatingLinkResolver matches literal URLs at 1.0, path-variable URLs at 0.8, segment matches at 0.6, prefix matches at 0.4
- [x] Unresolved calls with unmatched URLs are marked PENDING
