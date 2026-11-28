## 1. Evidence

- [x] 1.1 `scripts/capture-workflow-invocations.py` captures the router on 2.2.0 (87 rows, existing rows unchanged).

## 2. The action

- [ ] 2.1 `availability(cliVersion, routerFound)` (D2) and its tooltip, with unit tests for each state.
- [ ] 2.2 `AskAgentDialog` (D3), with a test seam like Explore's.
- [ ] 2.3 `OpenSpecAskAgentAction`: availability in `update()` from cached state, and the handoff through `WorkflowHandoffDelivery` (D4), Direct API included.
- [ ] 2.4 `plugin.xml`: the OpenSpec menu after Explore, and the tool-window toolbar (D5).
- [ ] 2.5 Tests:
  - the router's spelling for every captured 2.2.0 tool (`WorkflowInvocationContractTest`);
  - the handoff passes "verify add-auth, then archive it" through unchanged;
  - mutation checks: treat 2.1.1 as having the router; drop the skill check; rewrite the text.

## 3. Docs and verification

- [ ] 3.1 Feature reference, Marketplace page, CHANGELOG, and the parity inventory's invocation row.
- [ ] 3.2 `./gradlew build`, `openspec validate --all --strict`, and the maintainer's uiSmoke run.
