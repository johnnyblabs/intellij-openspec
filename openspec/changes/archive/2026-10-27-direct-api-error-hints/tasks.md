# Tasks

## 1. Fixtures

- [x] 1.1 Capture real error responses with fake or missing keys, and commit each as a body (`*.json`) plus the relevant response headers (`*.headers`: `content-type`, the request-ID header and `retry-after`; cookies and dates dropped) under `src/test/resources/fixtures/ai/<provider>/errors/`:
  - Claude: 401 `authentication_error` with `request-id`, from `/v1/models` and `/v1/messages`;
  - OpenAI: 401 with `x-request-id` and `content-type: text/plain`, from `/v1/models` and `/v1/chat/completions`;
  - Gemini: 400 `API_KEY_INVALID` and 403 `PERMISSION_DENIED`, from `models` and `:generateContent`.

  Verify: each file is real provider output, and no fixture contains a key or account identifier.
- [x] 1.2 Add the bodies that can't be captured on demand:
  - Claude spend-cap 429, verbatim from the rate-limits docs;
  - derived bodies: Claude 402 `billing_error`, Claude user-limit 400 (both documented message prefixes) and 529 `overloaded_error`; OpenAI 429 for each of the four quota or spend codes, `type insufficient_quota`, 503 `server_is_overloaded`, and the 400 `param: service_tier`; Gemini 402, 429 `RESOURCE_EXHAUSTED` and 503 `UNAVAILABLE`;
  - two controls: a Claude 401 whose header and body request IDs differ, and a Gemini 400 that is not a key error.

  Each derived body is the live envelope from 1.1 with only the documented field changed. Each fixture gets a row in `fixtures/ai/README.md` stating LIVE, DOCS or DERIVED, with the source page; the section gives the 2026-10-27 check date. Verify: every fixture under `errors/` has a README row (a test enforces this in 2.3).

## 2. Classifier and request ID

- [x] 2.1 Add `ErrorKind` and `DirectApiErrors.classify` with the matching rules of design D5 and the status fallback of D1, and one suggestion string per kind and provider. Verify with `DirectApiErrorsContractTest`, which loads every fixture from group 1 and asserts its kind. The test fails if:
  - a spend-cap 429 classifies as `RATE_LIMIT`;
  - an OpenAI quota code classifies as `RATE_LIMIT`;
  - a Gemini `API_KEY_INVALID` 400 doesn't classify as `AUTH`.
- [x] 2.2 Add `DirectApiErrors.requestId` (design D2). Verify:
  - the Claude header fixture yields its `req_` ID;
  - a Claude body-only input yields the body's `request_id`;
  - the OpenAI header fixture yields its ID;
  - Gemini and null headers yield none.
- [x] 2.3 Add a fixture-hygiene test. Verify:
  - every `errors/*.json` has a `.headers` sibling and a README row marked LIVE, DOCS or DERIVED;
  - no body or headers file matches a key pattern (`sk-` followed by key characters, `AIza`).
- [x] 2.4 Change `buildApiError` to take `HttpHeaders` (nullable); keep the old overloads; pass `response.headers()` from the three generation paths and `listModels`. Add `getKind()`, `getRequestId()` and `describe()` to `AiApiException`. Apply design D3 (parse whatever the content type) and D4 (workspace mapper, then the OpenAI scope 401 rule, then the classified suggestion, then the request ID). Verify:
  - billing, spend and quota suggestions never contain "retry";
  - a plain 429 and a 529 do;
  - the existing workspace and OpenAI scope tests pass unchanged;
  - the request ID ends the message, including on a mapped workspace message;
  - a non-JSON body (HTML 413) yields the truncated body as the message and the status-fallback kind;
  - the request ID is in the log line.
- [x] 2.5 Pin the request paths with a structural test, or extend `DirectApiFlowStructuralGuardTest`. Verify that every `buildApiError` call in `DirectApiService` passes the response headers. The test fails if a call site drops them.

## 3. Surfaces

- [x] 3.1 Rework `OpenSpecSettingsPanel.testFailureMessage` per design D6:
  - the OpenAI scope branch is chosen by kind plus the scope suggestion, not by text;
  - `AUTH` and `PERMISSION` give "Check your API key";
  - otherwise the result is `describe()`.

  Verify with unit tests over fixture-built exceptions: billing, spend, rate and overload show their suggestions; 401/403 keep today's wording; the OpenAI scope 401 keeps its wording.
- [x] 3.2 Make `ExploreContextAction` (panel and notification) and `VerificationService` (the "check unavailable" finding) use `describe()`. Verify with tests through the existing seams (`setRawRequestForTest`/`taskRunner` for Explore, the Verify AI path) that an `AiApiException` with a suggestion shows it.
- [x] 3.3 Make `WorkflowActionPanel`'s two existing suggestion appends use `describe()`, so every surface formats it the same way. Verify: the existing Generate and Generate All notification tests pass, and the surfaces' error text no longer formats the suggestion itself (grep `getSuggestion()` outside `ai/` finds only `testFailureMessage`'s kind check, if any).

## 4. Docs and gates

- [x] 4.1 Update `docs/feature-reference.md` (Direct API errors: the hint per error class, the request ID and where it appears) and add a CHANGELOG `## Unreleased` line written for users. Verify: `DocumentationHygieneTest` passes, and neither file names a tracker or internal host.
- [x] 4.2 Run `./gradlew build` (tests plus coverage floor) and `openspec validate direct-api-error-hints --strict`. Verify: both pass. Ratchet the JaCoCo floor up if coverage rose.

## Workflow follow-up

- Open the PR on the development remote and let CI run.
- Archive the change after merge, then close the trackers.
