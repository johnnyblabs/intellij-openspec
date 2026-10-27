# Tasks

## 1. Request settings and headers

- [x] 1.1 Add `ProviderRequestSettings` and switch every builder, `listModels` and the `call*` paths from `workspaceId` to it (design D1). Verify:
  - the existing Claude Workspace ID tests pass with only the parameter change;
  - Claude requests are unchanged (`Authorization`, `anthropic-version`, the workspace header only when set);
  - the Claude builder ignores the OpenAI fields.
- [x] 1.2 Send `OpenAI-Organization` and `OpenAI-Project` from one helper used by OpenAI generation and list-models (design D2). Verify with request-builder tests:
  - both headers present when both are set, on both request kinds;
  - neither header when both are blank;
  - only `OpenAI-Project` when only the project is set;
  - values are trimmed;
  - Claude and Gemini requests never carry either header.
- [x] 1.3 Add the status-based 401 suggestion for OpenAI when either field is set (design D4), and show it in Settings → Test too, which otherwise reduces every 401 to "Check your API key". Verify against live-captured invalid-key 401 bodies from both OpenAI endpoints:
  - a 401 with a field set keeps OpenAI's message and suggests checking the key and the IDs, in generation and in Test;
  - a 401 with both blank gives today's suggestion;
  - Claude workspace error mapping is unchanged.

## 2. Settings

- [x] 2.1 Add `aiOpenAiOrganization` and `aiOpenAiProject` to `OpenSpecSettings` (design D5). Verify:
  - they round-trip through state, trimmed, `""` when unset;
  - old state without them loads as `""`;
  - they never touch PasswordSafe.
- [x] 2.2 Add the two fields, labels and help text to the Direct API section, shown only for OpenAI, with prefix warnings that never block (design D3, D6); wire `OpenSpecConfigurable` `isModified`, `apply` and `reset`. Verify:
  - fields are visible for OpenAI and hidden for Claude and Gemini;
  - `validateOpenAiOrganization` and `validateOpenAiProject` warn on an unexpected prefix, accept blank, and never block apply;
  - Test sends the typed values;
  - the help text names when the fields are needed and where to find each ID, and says nothing about project or service-account keys.
- [x] 2.3 Pass `ProviderRequestSettings.from(settings)` from workflow-panel model discovery and every generation path. Verify:
  - the structural guard (`DirectApiFlowStructuralGuardTest`) shows discovery reads the saved settings inside its pooled task and passes them to `listModels`;
  - `generate`, `generateRaw` and `testConnection` send the saved IDs, and a 401 during generation or from the model list carries the exact ID hint that Settings → Test recognizes;
  - `from()` maps each stored field.

  Together with the list-models header test, these cover discovery without a weaker panel-level test.

## 3. Docs and gates

- [x] 3.1 Update the feature reference (Direct API settings: the two OpenAI fields and when they're needed) and add a CHANGELOG line. Verify: `DocumentationHygieneTest` passes, and the wording carries no claim about project or service-account keys.
- [x] 3.2 Mutation-check three things, each of which must fail a test and then be reverted:
  - always send `OpenAI-Organization`;
  - send the OpenAI headers on a Claude request;
  - block apply on a prefix warning.

  Also checked: the hint on a 403, Test sending the saved values, list-models without the headers, and `&&` for `||` in the scope check. All seven were caught.

  Then run `./gradlew build` and `./gradlew verifyPlugin`. Verify: green, 5/5 IDEs Compatible, no new deprecations.
