---
sessionId: session-261027-184110-1tk5
---

# Requirements

### Overview & Goals
Review the v0.15.0 release of the intellij-openspec plugin for **correctness bugs and regressions** and deliver a **report only**. No source changes, no fix patches.

v0.15.0 is not tagged yet. `build.gradle.kts` still says `0.14.1`, and the `## Unreleased` section of `CHANGELOG.md` is the 0.15.0 content. The review target is therefore `git diff v0.14.1..HEAD`. That is 109 commits, about 59 changed files under `src/main` (+5.2k/-0.6k lines), and about 235 changed test files.

### Scope
**In scope**
- Code changed since `v0.14.1` under `src/main/java` and `plugin.xml`.
- Behavior claimed in the `## Unreleased` changelog entries, checked against the code.
- Logic errors, edge cases, error handling, race and ordering problems, and regressions.
- IntelliJ threading and VFS misuse, but only where it causes a concrete bug.

**Out of scope**
- Unchanged code from before v0.14.1.
- Style or refactoring opinions.
- Test-quality audits, release-readiness and doc-fidelity audits, and the Direct API provider live-docs gate. The user chose correctness and bugs only.
- Any code or doc modification.

### Deliverable
One prioritized findings report. Each finding has a severity (Blocker, Major, Minor or Nit), a `file:line` reference, what goes wrong and under what conditions, and a suggested direction in one line. It also lists areas reviewed with no findings, so coverage is visible.

### Acceptance Criteria
- Every high-churn file in the diff is covered, or explicitly listed as skipped with a reason.
- Each finding is traced to real code, not guessed from the diff.
- Each changelog claim in the risky areas is checked against the implementation.

# Review Approach

### Current Implementation (what changed, by risk)
From `git diff --stat v0.14.1..HEAD -- src/main`, the review groups the changes into these areas. Largest churn first inside each group.

**A. Write and Direct API path (data-loss risk)**
- `toolwindow/ReviewedArtifactWriter.java` (new, 151 lines) and `toolwindow/ArtifactReviewDialog.java`, `ArtifactReviewer.java`
- `services/ArtifactLocalHistory.java` (new), `services/ArtifactOrchestrationService.java`
- `ai/DirectApiService.java`, `ai/DirectApiErrors.java` (new, 226 lines), `ai/AiApiException.java`, `ai/ProviderRequestSettings.java`
- `dialogs/RegenerateArtifactDialog.java`

**B. CLI detection and process execution (hang and environment risk)**
- `services/CliDetectionService.java` (+258 lines): IDE-PATH-first lookup, login-shell PATH probe, standalone installer paths, and `OPENSPEC_INSTALL_DIR` overrides.
- `util/CliRunner.java`, `util/CliInstallHints.java`
- `services/WorkflowProfileService.java`, `WorkflowProfileSwitchService.java`, `WorkflowInvocation.java`, `AiToolRegistry.java`

**C. Update preview and CLI-output parsing (parser risk)**
- `services/UpdatePreviewService.java`, `UpdatePreviewModel.java`, `util/UpdatePlanParser.java`, `dialogs/UpdatePreviewDialog.java`, `dialogs/ManageAiToolsDialog.java`, `actions/OpenSpecManageToolsAction.java`

**D. Explore, Revise, and tool window UI (state and EDT risk)**
- `toolwindow/WorkflowActionPanel.java` (+619), `OpenSpecToolWindowPanel.java` (+435), `SpecPreviewRenderer.java` (+384), `ExplorePanel.java`, `DiffVsCurrentSpec.java`
- `actions/ExploreContextAction.java` (+361), `ExploreChangeAction.java`, `ReviseChangeAction.java`, `OpenSpecOpenSettingsAction.java`
- `services/ExploreContextService.java` (+321), `ExplorePromptService.java`
- `model/ChangeArtifactDag.java`, `ArtifactInstruction.java`, `settings/OpenSpecSettingsPanel.java`, `dialogs/SetupWizardDialog.java`, `plugin.xml`

### Key Decisions
- Review the diff, not the whole tree. Read each changed file in full where it is touched heavily, because many of the new classes are net-new.
- Verify behavior claims against the code. Read a test only to learn intent or to see whether a case is covered at all.
- Run the existing build or test suite only if it helps confirm a suspected bug. Static reading is the primary method.

### Checklist per area
- **A (write path):** TOCTOU between the review and Accept (already double-checked in `ReviewedArtifactWriter`, so verify the line-separator and CRLF handling and partial-write behavior). `invokeAndWait` in `ArtifactLocalHistory.beforeWrite` when a caller holds a read lock. Local History label ordering. Error-to-suggestion mapping in `DirectApiErrors`, to confirm billing errors are not marked retryable and unknown shapes degrade safely. OpenAI and Gemini header handling. Thinking-block extraction.
- **B (CLI detection):** the login-shell probe's timeout, hang, and zombie-process handling. Rc files that print to stdout and break the marker parsing. Shell quoting and unusual `$SHELL` values (fish, nu, csh). Where it runs (not EDT, not under a read lock). PATH merge order and duplicates. Windows branch behavior. Caching and invalidation. Whether a found binary is verified as executable.
- **C (update preview):** the parser against real CLI output shapes. Empty, partial, malformed and CLI-version-skew output. Fallback when the CLI is older than 1.15. The `Run in terminal` flow and the legacy-cleanup dismissal memory.
- **D (UI and Explore):** EDT and background-thread discipline. Disposal and listener leaks. Selection and expansion restore after refresh. State left disabled after errors. Null and empty handling for nested capabilities and custom-schema artifacts. Workflow-profile gating consistency across the menu, toolbar and tabs. `plugin.xml` registrations, including ids, `since-build`, and dynamic-plugin safety.

# Testing

### Validation Approach
Nothing is modified, so validation concerns the quality of the findings, not code behavior.

### Key Scenarios
- Each reported bug is confirmed by reading the actual call path. The report names the trigger conditions.
- Where a finding is cheap to confirm, it is confirmed by running the relevant existing unit test class with `./gradlew test --tests <Class>`, or by reading the existing test to show the case is untested.

### Edge Cases
- Do not report a finding already handled elsewhere. Check the callers and the tests first.
- Label unconfirmed concerns as **Suspected** with what is needed to confirm them, and keep them separate from confirmed bugs.

### Test Changes
None. No tests are added or changed.

# Delivery Steps

### * Step 1: Review write path and Direct API changes
Findings for the write and Direct API area are recorded with severity and file:line references.

- Read in full: `ReviewedArtifactWriter`, `ArtifactReviewDialog`, `ArtifactReviewer`, `ArtifactLocalHistory`, `ArtifactOrchestrationService` diff, `RegenerateArtifactDialog`.
- Read in full: `DirectApiService` diff, `DirectApiErrors`, `AiApiException`, `ProviderRequestSettings`.
- Check TOCTOU, CRLF handling, partial writes, undo behavior, and `invokeAndWait` and read-lock hazards.
- Check error classification and retry advice for billing, permission and unknown shapes.
- Check per-provider headers and response parsing against the changelog claims.

###   Step 2: Review CLI detection, process execution and workflow profile changes
Findings for CLI discovery and workflow gating are recorded.

- Read in full: `CliDetectionService` (login-shell PATH probe, standalone-installer paths, `OPENSPEC_INSTALL_*` overrides), `CliRunner`, `CliInstallHints`.
- Check shell-spawn timeouts, hangs and process cleanup, noisy rc files, non-POSIX shells, threading, caching, and the Windows branch.
- Read in full: `WorkflowProfileService`, `WorkflowProfileSwitchService`, `WorkflowInvocation`, `AiToolRegistry`.
- Check core-profile workflow resolution against the changelog, and gating consistency.

###   Step 3: Review Update preview and CLI-output parsing
Findings for the Update preview flow and its parser are recorded.

- Read in full: `UpdatePlanParser`, `UpdatePreviewService`, `UpdatePreviewModel`, `UpdatePreviewDialog`.
- Read the changed parts of `ManageAiToolsDialog`, `OpenSpecManageToolsAction` and `SetupWizardDialog`.
- Check empty, partial, malformed and version-skewed CLI output, the pre-1.15 fallback, and the legacy-cleanup and Run-in-terminal paths.
- Compare parsing assumptions with the captured fixtures under `src/test/resources/fixtures/cli/`, and flag shapes no fixture covers.

###   Step 4: Review Explore, Revise and tool window UI changes
Findings for the UI, Explore and Revise areas are recorded.

- Read the heavily changed `WorkflowActionPanel`, `OpenSpecToolWindowPanel`, `SpecPreviewRenderer`, `ExplorePanel`, `DiffVsCurrentSpec`.
- Read `ExploreContextAction`, `ExploreChangeAction`, `ReviseChangeAction`, `ExploreContextService`, `ExplorePromptService`, `ChangeArtifactDag`, `ArtifactInstruction`.
- Check EDT and background-thread use, disposal and listener leaks, refresh and selection restore, disabled-state recovery, and nested-capability and custom-schema handling.
- Review the `plugin.xml` changes and `OpenSpecSettingsPanel`.

###   Step 5: Compile the prioritized findings report
A single report is delivered to the user, with no code changes.

- Merge the per-area findings, deduplicate them, and sort by severity (Blocker, Major, Minor, Nit).
- Give each finding a `file:line` reference, trigger conditions, impact, and a one-line suggested direction. Mark unconfirmed items as Suspected.
- Add the areas reviewed with no findings and any files deliberately skipped.
- Give a short overall assessment of whether the findings block tagging v0.15.0.