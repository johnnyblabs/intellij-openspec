# Design

## Context

Direct API requests are built by static, unit-testable builders in `DirectApiService`: `buildClaudeRequest`, `buildOpenAiRequest`, `buildGeminiRequest` and `buildListModelsRequest`. The Claude Workspace ID is the only provider-specific request setting today. It travels as a separate `@Nullable String workspaceId` parameter through every Claude path:
- generation (`callClaude`, from three call sites that read `OpenSpecSettings`);
- Test (the settings panel passes the typed value to `listModels`);
- model discovery in the workflow panel (`WorkflowActionPanel` reads the stored value).

`buildOpenAiRequest` and the OpenAI branch of `buildListModelsRequest` send only `Authorization`.

Error mapping lives in the static `buildApiError(provider, status, body)`. A 401 gets the generic "Check your API key" suggestion, and Claude-only body matching rewrites workspace errors.

What OpenAI documents, checked live on 2026-10-27:
- The header names are `OpenAI-Organization` and `OpenAI-Project`. They are needed for legacy user keys or multi-org users; without them, the key's defaults apply.
- Project IDs carry the `proj_` prefix. Organization IDs are shown as `org-…` in examples only.
- Identifier formats may change without notice.
- A mismatch's error body and code are not documented.

## Goals / Non-Goals

**Goals:**
- One carrier for provider-specific request settings, so adding a field doesn't mean threading another parameter through every call path.
- Header behaviour that is exactly testable from the request builders, for both OpenAI request kinds.

**Non-Goals:**
- No new error-body parsing for OpenAI; that waits for a live capture.
- No change to Claude Workspace ID validation, which stays strict, as specified.

## Decisions

**1. A `ProviderRequestSettings` record replaces the `workspaceId` parameter.**
- The record holds `claudeWorkspaceId`, `openAiOrganization` and `openAiProject`, each trimmed and never null (blank means unset).
- It has two factories: `from(OpenSpecSettings)`, for stored values, and `NONE`.
- The builders, `listModels` and the private `call*` methods take it in place of `workspaceId`. Each provider reads only its own fields, so the Claude builder ignores the OpenAI fields and vice versa.
- Alternative: add two more `@Nullable String` parameters. Rejected, because every call site and test would grow per field, and the tracker entry explicitly asked for the pattern to be built once.
- Alternative: a per-provider map of header names to values. Rejected, because it would hide the header names from the type and let a setting leak to the wrong provider.

**2. OpenAI headers are added in one helper shared by generation and list-models,** mirroring `addClaudeAuthHeaders`. A header is added only for a non-blank field, so requests with both fields blank have exactly today's headers.

**3. Prefix checks warn and never block.**
- `validateOpenAiOrganization` and `validateOpenAiProject` return a warning string or null, so they can be unit-tested. A warning label under each field shows that string while the field is visible, updated as the user types.
- Why a label and not `ComponentValidator` with `ValidationInfo.asWarning()`: the settings panel owns no `Disposable` to parent a validator to. Parenting it to the project would pile validators up across light tests, which reuse the project.
- `OpenSpecConfigurable.apply` does not reject on them, and Test sends the value as typed.
- Alternative: block like the Workspace ID. Rejected, because OpenAI documents the org prefix only by example and reserves the right to change identifier formats. A blocking check could lock out a valid ID.
- The Workspace ID keeps its blocking check, because Anthropic documents its format and it is already specified that way.

**4. The 401 hint is status-based.**
- `buildApiError` gains the request settings (an overload; the existing signature delegates with `NONE`). For OpenAI with status 401 and either field set, the suggestion becomes: check the API key, and that the Organization and Project IDs in the Direct API settings belong to it. The message stays OpenAI's own.
- Alternative: match the error body. Rejected, because the body for a mismatch is undocumented and there is no capture to contract-test against.

**5. Storage mirrors the Workspace ID.**
- There are two new `OpenSpecSettings` state fields, `aiOpenAiOrganization` and `aiOpenAiProject`, which are project-level, trimmed on set and `""` when unset.
- They are not secrets, so they never go to PasswordSafe.
- The settings panel holds the fields, labels and help, and toggles their visibility synchronously on provider change, next to the Workspace ID toggle.
- `OpenSpecConfigurable`'s `isModified`, `apply` and `reset` cover them.

**6. Help text.** "Only needed for legacy user API keys or if you belong to more than one organization." Each field also says where its ID is found: Settings → Organization → General, or Settings → Project → General. There is no statement about project or service-account keys.

## Risks / Trade-offs

- **[A project or service-account key might reject a mismatched header]** OpenAI documents neither case. → The fields are blank by default, the help text says when they're needed, and a 401 points at them.
- **[Changing the builder signatures touches existing Claude tests]** → The Claude header assertions and messages stay byte-for-byte the same; only how the Workspace ID is passed in changes. The existing workspace tests must pass with only that edit.
- **[The warning could be missed]** → It never blocks, and a wrong value surfaces as a 401 with the new hint.

## Migration Plan

Existing settings load with both new fields `""`, so no headers are sent and behaviour is unchanged. Rollback is a revert. The stored fields are ignored by older plugin versions.
