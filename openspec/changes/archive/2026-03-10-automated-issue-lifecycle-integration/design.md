## Context

After completing a change lifecycle (propose → generate → apply → archive), users manually create/update issues in Forgejo and work items in Plane. The project already has shell scripts (`scripts/lib/forgejo-api.sh`, `scripts/lib/plane-api.sh`) that demonstrate the REST API patterns for both systems. The plugin has an established pattern for credential storage via `AiCredentialStore` using IntelliJ's `PasswordSafe`, and for HTTP calls via Java 11+ `HttpClient` with Gson (as in `DirectApiService`).

The `.openspec.yaml` file in each change directory currently stores `schema` and `created` fields — it will be extended to store tracker references.

## Goals / Non-Goals

**Goals:**
- Automatically create a Forgejo issue and Plane work item when a change is proposed
- Update issue/work-item status when Apply and Archive actions are triggered
- Store issue/work-item IDs in `.openspec.yaml` for bidirectional linking
- Provide settings UI for configuring tracker connections (URLs, credentials, enable/disable)
- Show linked issue status in the tool window tree
- Make tracking fully optional — the plugin works identically without it configured

**Non-Goals:**
- Syncing task-level progress to individual sub-issues (too granular for v1)
- Two-way sync from tracker → OpenSpec (changes in Forgejo/Plane don't flow back)
- Supporting trackers beyond Forgejo and Plane (GitHub Issues, Jira, Linear, etc.)
- Webhook-based real-time sync (polling or event-driven sync on user action is sufficient)
- Managing labels, milestones, or cycles from the plugin (handled by setup scripts)

## Decisions

### 1. Credential storage: Extend PasswordSafe pattern

**Decision:** Create a `TrackerCredentialStore` utility class following the same pattern as `AiCredentialStore`, using IntelliJ `PasswordSafe` with a distinct service prefix (`OpenSpec-Tracker-`).

**Rationale:** Consistent with existing credential management. PasswordSafe handles encryption and platform-specific secure storage. Separating from `AiCredentialStore` keeps concerns clean — tracker tokens are not AI provider keys.

**Alternative considered:** Storing tokens in `OpenSpecSettings.State` — rejected because that persists to `openspec.xml` in plaintext.

### 2. Service architecture: Three services

**Decision:**
- `ForgejoService` — Low-level Forgejo REST API client (create issue, update issue, get issue)
- `PlaneService` — Low-level Plane REST API client (create work item, update work item, get work item)
- `IssueLifecycleService` — Orchestrator that coordinates both trackers during lifecycle events

**Rationale:** Separating API clients from lifecycle logic allows independent testing and potential reuse. The orchestrator pattern matches `ArtifactOrchestrationService` which coordinates artifact generation across providers.

**Alternative considered:** Single `IssueTrackingService` doing everything — rejected because the two APIs are different enough that combining them makes the class unwieldy and harder to test.

### 3. Lifecycle event integration: Direct calls from actions

**Decision:** Each action (`Propose`, `Apply`, `Archive`) calls `IssueLifecycleService` directly after its primary operation succeeds. Tracking operations run on a background thread and show a notification on success/failure. Tracking failures do NOT block the primary action.

**Rationale:** Simplest approach. Message bus events were considered but add complexity for three well-defined integration points. Non-blocking ensures a Forgejo outage doesn't prevent the user from archiving a change.

**Alternative considered:** IntelliJ `MessageBus` topic for lifecycle events — rejected as over-engineering for 3 callers. Can migrate later if more consumers emerge.

### 4. Metadata storage: `tracking` block in `.openspec.yaml`

**Decision:** Add an optional `tracking` block to `.openspec.yaml`:
```yaml
schema: spec-driven
created: 2026-03-10
tracking:
  forgejo:
    issueNumber: 42
    issueUrl: "http://forgejo.geek/johnb/OpenSpecPlugin/issues/42"
  plane:
    workItemId: "abc-123"
    workItemUrl: "http://plane.geek/openspec/projects/..."
```

**Rationale:** Keeps tracking metadata co-located with the change. The `ChangeService` already reads `.openspec.yaml` — extending the parser is straightforward. Optional block means existing changes work without modification.

### 5. Settings UI: New "Issue Tracking" section

**Decision:** Add an "Issue Tracking" section to the settings panel below "Direct API". Two sub-groups: Forgejo (URL, token, repo owner/name, enable checkbox) and Plane (URL, API key, workspace, project, enable checkbox). Each sub-group has a "Test Connection" button.

**Rationale:** Follows the existing settings panel pattern (single scrollable panel with titled sections). The "Test Connection" button mirrors the "Test" button pattern in the Direct API section.

### 6. Issue content: Generated from proposal.md

**Decision:** When creating an issue/work-item on Propose, the system reads `proposal.md` and uses:
- Title: Change name in title case (e.g., "Automated Issue Lifecycle Integration")
- Body: Full content of proposal.md as the issue body (markdown for Forgejo, HTML for Plane)

On Apply: Add a comment "Implementation started — Apply triggered" and update label to `in-progress`.
On Archive: Close the issue, add a comment "Change archived", and update label to `done`.

**Rationale:** Keeps issue content meaningful and connected to the change. Using proposal.md as the body provides full context in the tracker without manual copy-paste.

### 7. Label management: Use existing labels

**Decision:** The plugin assumes labels (`enhancement`, `in-progress`, `done`) already exist in both trackers (created by setup scripts). If a label is missing, the operation proceeds without it and logs a warning.

**Rationale:** Setup scripts already handle label creation. The plugin should not manage label lifecycle — that's an admin concern.

## Risks / Trade-offs

- **[Network dependency]** → Tracking calls are fire-and-forget on background threads with notifications. Connection failures show a warning but never block the user's workflow.
- **[Stale metadata]** → If an issue is deleted in Forgejo/Plane, the `.openspec.yaml` reference becomes stale. Mitigation: the plugin validates the reference on first access and clears stale refs.
- **[Credential rotation]** → If a token expires or is revoked, tracking silently fails until the user updates credentials. Mitigation: "Test Connection" button in settings; clear error notifications on failure.
- **[Markdown → HTML conversion for Plane]** → Plane's API requires `description_html`. Basic conversion (headings, lists, paragraphs) is sufficient; complex markdown (tables, code blocks) may render imperfectly. Acceptable for v1.
- **[Multiple projects]** → Currently assumes one Forgejo repo and one Plane project. If users work across projects, they'd need to reconfigure. Acceptable for v1 given single-project typical usage.
