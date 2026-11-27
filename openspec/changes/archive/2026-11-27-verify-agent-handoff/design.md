# Design

## Context

Upstream's `verify` workflow (`openspec-verify-change`) runs in the agent and reports completeness, correctness and coherence. The plugin's Verify report (`VerifyDialog`, report form since the previous change) shows a deterministic readiness check, plus an AI correctness check only when Direct API is configured. Continue and Apply already hand their skill to the AI tool through `WorkflowInvocation.find` and `WorkflowHandoffDelivery.deliver`, in Clipboard and Editor Tab modes.

## Goals / Non-Goals

**Goals:**
- Every delivery mode reaches the full verify: Direct API in the IDE, the others through the agent's verify skill.
- Keep the deterministic report and the Archive… hand-off everywhere.

**Non-Goals:**
- Replacing the report with the hand-off.
- Changing the Archive action's pre-flight, or the in-IDE Direct API correctness check.

## Decisions

### D1. A hand-off button on the report

`VerifyDialog.report` takes an optional hand-off label. When set, a **Verify with <tool>…** button closes the dialog with its own exit code (`HANDOFF_EXIT_CODE`), distinct from Close and Archive…. The callers then deliver the hand-off. The label is a pure function of the delivery mode and what `WorkflowInvocation.find` returned:
- Direct API: no button.
- A tool with the verify skill or command: "Verify with <tool name>…".
- None: "Verify with your AI tool…".

### D2. The hand-off is Continue's

`VerifyHandoff.handoff` is `WorkflowInvocation.handoff(invocation, "openspec-verify-change", changeName)`, so each tool gets its own spelling (`/openspec-verify-change`, `$openspec-verify-change` for Codex, `/opsx:verify` on 1.x, or the plain-words form), as captured in every release's `workflow-invocations.json`. Delivery is `WorkflowHandoffDelivery.deliver` in the active mode. With no verify skill or command, the click shows Continue's not-installed notice, worded for Verify, with an Update action.

### D3. Where it is decided

The delivery mode and `WorkflowInvocation.find` (which reads the disk) run in the Verify task's background thread, beside the readiness check. The dialog receives only the label.

## Risks / Trade-offs

- **The correctness line still says "not assessed (AI provider not configured)" beside the hand-off button.** Accepted: it describes the in-IDE check; the button is the route to the agent's.

## Migration Plan

None.

## Open Questions

None.
