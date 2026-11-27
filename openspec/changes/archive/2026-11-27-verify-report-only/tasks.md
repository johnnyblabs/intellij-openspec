# Tasks

## 1. Dialog

- [x] 1.1 `VerifyDialog` takes a form (REPORT, ARCHIVE_PREFLIGHT) and, for REPORT, whether archiving is offered. REPORT: OK reads "Close", always enabled; an Archive… action ("Archive…" READY, "Archive anyway…" IN PROGRESS, disabled BLOCKED) only when archiving is offered, closing with its own exit code. ARCHIVE_PREFLIGHT: unchanged (D1).
- [x] 1.2 Pure label and enablement functions for both forms.

## 2. Callers

- [x] 2.1 `OpenSpecVerifyAction`: REPORT form, archive offered per `isWorkflowEnabled("archive")`; archives only on the Archive… exit code (D2).
- [x] 2.2 `WorkflowActionPanel.onVerify`: the same; "Archive anyway…" doesn't ask the open-task question again (Risks).
- [x] 2.3 `OpenSpecArchiveAction`: ARCHIVE_PREFLIGHT form, behaviour unchanged.

## 3. Tests

- [x] 3.1 Button contract per form × state × archive offered: REPORT OK is "Close" and enabled in every state; Archive… is absent without `archive`, labelled per state, disabled for BLOCKED; ARCHIVE_PREFLIGHT keeps today's labels and enablement.
- [x] 3.2 The Verify action and the panel archive only on the Archive… exit code: closing the report (OK) never archives, and a profile without `archive` never offers it.
- [x] 3.3 Mutations, each turning a test red: REPORT OK archives; Archive… shown without `archive`; BLOCKED Archive… enabled.

## 4. Docs and verification

- [x] 4.1 Feature reference (Verify) and a CHANGELOG entry under Unreleased.
- [x] 4.2 `./gradlew build`, `openspec validate --all --strict`; uiSmoke if a journey covers the Verify dialog. No journey opens the Verify report; the Archive journey uses the unchanged pre-flight.
