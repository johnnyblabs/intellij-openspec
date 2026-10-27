# Proposal

## Why

OpenAI asks callers who use a legacy user API key, or who belong to more than one organization, to name the organization and project with the `OpenAI-Organization` and `OpenAI-Project` headers. Without them, usage is billed to the default organization or project. The plugin sends neither header, so those users can't choose where Direct API usage is billed or which project's access applies. The Direct API release gate's request-header audit found the gap. Tracked by the linked tracker entry.

## What Changes

- **Two optional OpenAI settings.** The Direct API settings gain **Organization ID** and **Project ID** fields, shown only when OpenAI is the selected provider. Both may be left blank.
- **Sent only when set.** A set Organization ID is sent as `OpenAI-Organization`, and a set Project ID as `OpenAI-Project`, on every OpenAI request: generation and the model list. A blank field sends no header, so today's requests are unchanged for everyone who leaves them empty. Test uses the values currently typed in the form, like the Claude Workspace ID.
- **Lenient format hints.** OpenAI documents the `proj_` prefix for project IDs and shows `org-` in its organization examples, and it treats identifier formats as free to change. A value with an unexpected prefix gets a warning under the field but is still saved and sent. Nothing is checked beyond the prefix.
- **Help text says only what the docs say.** The fields are needed for legacy user keys or when you belong to several organizations; otherwise leave them blank. The help names where each ID is found (Settings → Organization → General, and Settings → Project → General). It makes no claim about project or service-account keys, because OpenAI doesn't document them.
- **A 401 points at these fields when they are set.** When OpenAI returns 401 and either field is set, the error suggestion also asks the user to check that the Organization and Project IDs belong to this key. OpenAI's own message is kept, and nothing is parsed from the error body, which is undocumented for this case.
- **One pattern for provider-specific request settings.** The Claude Workspace ID and the two OpenAI fields are carried together as the provider's request settings, so generation, Test and model discovery pass one value instead of growing a parameter per field. The Claude Workspace ID's behaviour, validation and messages are unchanged.
- These are plain settings, not secrets: stored as regular settings, never in PasswordSafe, and never written to any OpenSpec project file.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-integration`: "AI providers" gains the optional OpenAI Organization ID and Project ID settings and their headers, and "Test connection" uses the typed OpenAI values. Adds "OpenAI organization and project errors" for the 401 hint.

## Impact

- **Code:**
  - `DirectApiService`: OpenAI generation and list-models requests carry the two headers when set; a 401 gets the extra suggestion when either is set; one request-settings value replaces the Claude-only `workspaceId` parameter.
  - `OpenSpecSettings`: two new plain string settings.
  - `OpenSpecSettingsPanel` and `OpenSpecConfigurable`: the two fields, shown for OpenAI only, with the prefix warning; apply, reset and is-modified.
  - `WorkflowActionPanel`: model discovery passes the request settings.
- **Tests:**
  - request-builder tests for both headers, present when set, absent when blank, on generation and list-models;
  - settings round-trip and visibility per provider;
  - the prefix warning never blocks saving or sending;
  - the 401 suggestion appears only when a field is set;
  - the existing Claude Workspace ID tests keep passing unchanged.
- **Docs:** the feature reference and the CHANGELOG.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported. No new platform APIs; `verifyPlugin` gates this.
- **Out of scope:**
  - mapping specific OpenAI error codes for a mismatched project (undocumented; needs a live capture first);
  - OpenAI regional data-residency hosts (tracked separately);
  - Gemini settings of any kind.
