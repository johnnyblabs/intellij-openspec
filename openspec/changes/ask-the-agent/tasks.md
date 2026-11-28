## 1. Evidence

- [x] 1.1 `scripts/capture-workflow-invocations.py` captures the router on 2.2.0 (87 rows, existing rows unchanged).

## 2. The action

- [x] 2.1 Availability (D2): hidden below CLI 2.2.0 from the cached version, so `update()` reads no disk; with no router skill, a warning with Update OpenSpec on click, as Continue does. Unit-tested.
- [x] 2.2 `AskAgentDialog` (D3), with a test seam like Explore's.
- [x] 2.3 `OpenSpecAskAgentAction`: availability in `update()` from cached state, and the handoff through `WorkflowHandoffDelivery` (D4), Direct API included.
- [x] 2.4 `plugin.xml`: the OpenSpec menu after Explore, and the tool-window toolbar (D5).
- [x] 2.5 Tests:
  - the router's spelling for every captured 2.2.0 tool (`WorkflowInvocationContractTest`);
  - the handoff passes "verify add-auth, then archive it" through unchanged;
  - mutation checks: treat 2.1.1 as having the router; drop the skill check; rewrite the text.

## 3. Docs and verification

- [x] 3.1 Feature reference, Marketplace page, CHANGELOG, and the parity inventory's invocation row.
- [x] 3.2 `./gradlew build`, `openspec validate --all --strict`, and the maintainer's uiSmoke run.
