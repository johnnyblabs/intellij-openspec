## Context

`DirectApiService.callGemini` currently constructs the request URL as:

```java
String url = GEMINI_API_URL + model + ":generateContent?key=" + apiKey;
```

The API key embedded in the URL travels through every component that records URLs — IDE HTTP traces, sandbox proxies, server access logs, error-reporting telemetry. Google's own client libraries default to the `x-goog-api-key` header for this reason; the URL-query form survives in their docs as a "convenience" pattern but is not the recommended form. Both auth methods are accepted by the Gemini API today, so the migration is a no-functional-change hygiene fix.

The `callGemini` method also can't be unit-tested for header presence today — the request is built and sent inline, with `createHttpClient().send(...)` reaching the network. A small extract-helper refactor (`buildGeminiRequest(model, apiKey, prompt) → HttpRequest`) makes the request shape assertable without a live API.

The companion `openAiTokenParam` test isn't a behavior change — it just locks the current `o1`-prefix routing in place. It exists because a future change *will* extend the prefix list (when new reasoning-model families are confirmed), and pinning the current behavior with a test means that change can be additive without regressing what works today.

## Goals / Non-Goals

**Goals:**
- Gemini API keys no longer appear in request URLs in any form.
- The `x-goog-api-key` header carries the key on every Gemini call.
- A unit test asserts the auth-header migration without depending on the network.
- The current `openAiTokenParam` routing is documented by tests so a future extension can be made safely.

**Non-Goals:**
- Refactoring `DirectApiService` more broadly. Only the Gemini path gets the helper extraction; OpenAI and Claude paths stay inline (they don't have testability gaps that this change is solving).
- Adding new test infrastructure (mock HttpClient, recorded responses). Tests use `HttpRequest.uri()` and `HttpRequest.headers().firstValue(...)` directly on the constructed request — pure synchronous, no I/O.
- Removing the URL-query auth fallback for backward compatibility. Gemini's API accepts both, but we want one contract; just send the header.

## Decisions

### Decision 1: Migrate Gemini auth to the `x-goog-api-key` header

**Choice:** The Gemini API key is sent exclusively via the `x-goog-api-key` header. The URL has no `?key=` query parameter.

**Rationale:** Header-based auth is the documented Google convention and avoids the leakage surfaces (logs, inspectors, telemetry) that URL-embedded keys are vulnerable to. Both forms are accepted by the API, so this is a strict improvement with no functional cost.

**Alternatives considered:**
- Send the key in both header and URL for "double safety". Rejected: doesn't reduce leakage and adds redundant exposure.
- Authorization-header style (`Authorization: Bearer <key>`). Rejected: not how Gemini's API expects the key; would require a different format.

### Decision 2: Extract `buildGeminiRequest` for testability; leave OpenAI/Claude builders inline

**Choice:** Add a package-private static method `buildGeminiRequest(String model, String apiKey, String prompt) → HttpRequest`. `callGemini` becomes a thin wrapper that calls the builder and sends the result. OpenAI and Claude builders stay inline.

**Rationale:** Only Gemini has a request-shape change in this scope, so only Gemini needs the extraction. Refactoring all three to look symmetric is busywork — it doesn't make any of them better today. Future changes that need to assert OpenAI/Claude request shapes can extract their builders then. Symmetry-for-its-own-sake is a maintenance trap.

**Alternatives considered:**
- Refactor all three providers to symmetric `buildXxxRequest` helpers. Rejected as above.
- Use a `RequestRecorder` HttpClient subclass to intercept calls. Rejected: more infrastructure than the problem warrants.

### Decision 3: Pin `openAiTokenParam` behavior with a test, don't change it

**Choice:** Add a test class that asserts `openAiTokenParam("o1-mini") == "max_completion_tokens"`, `openAiTokenParam("gpt-4o") == "max_tokens"`, and `openAiTokenParam(null)`'s current behavior (whatever it does — likely throws NPE). No code change to `openAiTokenParam` itself.

**Rationale:** The engineering review correctly flagged that *expanding* the prefix list to `o3`/`o4`/`gpt-5` requires confirming those models exist. We're not doing that here. But the test is still valuable: it locks the current contract, so when that future expansion change happens, the existing-prefix routing can't silently break. Free safety net.

**Alternatives considered:**
- Skip the test entirely. Rejected: it's cheap (~10 lines) and prevents a real category of regression.
- Add the new prefixes anyway. Rejected: that's the unverified scope this change deliberately defers.

### Decision 4: Don't bundle CHANGELOG mention

**Choice:** No CHANGELOG entry for this change. The Gemini header migration is internal hygiene; users see no behavior difference.

**Rationale:** The CHANGELOG is for plugin users (per saved feedback memory). Header-vs-URL auth doesn't affect anything they observe — the Gemini API works the same way. Documenting "we moved a key from one transport to another" reads as noise on the Marketplace listing.

**Alternatives considered:**
- Mention under a "Security" subsection. Rejected — overstates the impact for a single-user-key plugin where the leakage surface is already inside one user's IDE.
- Document in commit message + design.md only (current choice). Future-maintainer audit trail without polluting user-facing notes.

## Risks / Trade-offs

- **Risk:** A future Gemini API version deprecates URL-query auth and starts rejecting the alternate form. → Mitigation: irrelevant; we're moving to the recommended form, not the deprecated one.
- **Risk:** A test against `HttpRequest` headers/URI relies on internal `java.net.http` shape that could shift in future JDKs. → Mitigation: low — `HttpRequest.uri()` and `HttpRequest.headers()` are stable public API in JDK 11+.
- **Trade-off:** Extracting only the Gemini builder leaves OpenAI/Claude paths asymmetric. Acceptable: no other change in scope needs to assert their shape. Future symmetry can come when there's a reason for it.

## Migration Plan

- No persisted-state migration. URLs in flight don't matter; existing user configurations remain valid.
- Rollout: ships in v0.2.10 patch release.
- Rollback: revert. Single method change + one new helper + tests.

## Open Questions

- None blocking. The deferred items (model lists, anthropic-version, MAX_TOKENS, openAi prefix expansion) are tracked as out-of-scope in the proposal and will land in a separate change once their facts are verified.
