---
name: close-change-trackers
description: Close the tracker shadow of an archived OpenSpec change — close the Forgejo issue with a completion comment and move the Plane work item to Done. Invoke after `openspec-archive-change`. Reads tracker IDs from the archived proposal.md.
license: MIT
compatibility: Requires scripts/.env with FORGEJO_URL/FORGEJO_TOKEN/PLANE_URL/PLANE_WORKSPACE/PLANE_API_KEY for the close ops. Will migrate to homelab MCP once it gains state-transition tools.
---

Close the Forgejo issue and move the Plane work item to Done for an archived change. Replaces the inline curl plumbing that used to live in `openspec-archive-change` Step 6. The new path is durable across `openspec update` because this skill lives at a custom name OpenSpec doesn't manage.

**Implementation note:** currently uses inline curl because the homelab MCP server has no state-transition tools (it can create issues and cards, not close them). When the MCP gains `forgejo_close_issue` and `plane_move_card_state`, migrate this skill to call those instead. The LOCATION of the customization is durable regardless of the implementation.

**Input**: the archived change directory name (e.g., `2026-06-12-schema-validation-cli-runtime-driven`) OR the bare change name (the skill will resolve to the most recent matching archive).

**Steps**

1. **Resolve the archived proposal.md**

   ```bash
   PROPOSAL=openspec/changes/archive/<archive-dir>/proposal.md
   ```

   If the argument was a bare change name (no date prefix), find the most recent archive matching `*-<change-name>` and use that one.

   If the proposal doesn't exist, report and stop — there's nothing to close.

2. **Parse tracker IDs**

   Primary source — the gitignored `.tracking.yaml` sidecar written by `mirror-change-trackers`:

   ```bash
   TRACKING=openspec/changes/archive/<archive-dir>/.tracking.yaml

   if [[ -f "$TRACKING" ]]; then
       FORGEJO_ISSUE=$(grep -E '^\s*issue:' "$TRACKING" | grep -oE '[0-9]+' | head -1)
       PLANE_WORK_ITEM=$(grep -E '^\s*id:\s*[a-f0-9-]{36}' "$TRACKING" | grep -oE '[a-f0-9-]{36}' | head -1)
   fi
   ```

   **Fallback for historical archives** (pre-2026-06-14, before the sidecar convention) — parse `## References` in `proposal.md`:

   ```bash
   if [[ -z "$FORGEJO_ISSUE" && -z "$PLANE_WORK_ITEM" ]]; then
       PROPOSAL=openspec/changes/archive/<archive-dir>/proposal.md
       FORGEJO_ISSUE=$(grep -oE 'Forgejo:\s*johnb/intellij-openspec#[0-9]+' "$PROPOSAL" | grep -oE '[0-9]+$' | head -1)
       PLANE_WORK_ITEM=$(grep -oE 'Plane:[^`]*`[a-f0-9-]{36}`' "$PROPOSAL" | grep -oE '[a-f0-9-]{36}' | head -1)
   fi
   ```

   **If no IDs found in either source:** skip silently. The change may pre-date tracker mirroring entirely.

3. **Check for credentials**

   ```bash
   test -f scripts/.env && source scripts/.env
   ```

   If `scripts/.env` is missing or `FORGEJO_TOKEN` / `PLANE_API_KEY` empty: log `Tracker closure skipped — credentials not found` and stop. Archive is already done; this is a best-effort sidecar.

4. **Close the Forgejo issue**

   ```bash
   source scripts/.env
   FORGEJO_API="${FORGEJO_URL}/api/v1/repos/johnb/intellij-openspec"

   curl -s -X PATCH \
     -H "Authorization: token ${FORGEJO_TOKEN}" \
     -H "Content-Type: application/json" \
     "${FORGEJO_API}/issues/${FORGEJO_ISSUE}" \
     -d '{"state": "closed"}'

   # Completion comment — version from build.gradle.kts, change name from the archive dir
   VERSION=$(grep '^version = ' build.gradle.kts | head -1 | grep -oE '"[^"]+"' | tr -d '"')
   curl -s -X POST \
     -H "Authorization: token ${FORGEJO_TOKEN}" \
     -H "Content-Type: application/json" \
     "${FORGEJO_API}/issues/${FORGEJO_ISSUE}/comments" \
     -d "$(python3 -c "import json; print(json.dumps({'body': f'Archived in change \`<change-name>\`. Version target: v${VERSION}.'}))")"
   ```

5. **Move Plane work item to Done**

   ```bash
   PLANE_PROJECT_ID="d358203d-16dd-48c4-ba22-f82be6781dd2"
   PLANE_WS_API="${PLANE_URL}/api/v1/workspaces/${PLANE_WORKSPACE}/projects/${PLANE_PROJECT_ID}"

   DONE_STATE_ID=$(curl -s -H "X-API-Key: ${PLANE_API_KEY}" \
     "${PLANE_WS_API}/states/" | \
     python3 -c "import sys,json; r=json.load(sys.stdin); states=r.get('results',r); print(next((s['id'] for s in states if s['name']=='Done'), ''))")

   curl -s -X PATCH \
     -H "X-API-Key: ${PLANE_API_KEY}" \
     -H "Content-Type: application/json" \
     "${PLANE_WS_API}/work-items/${PLANE_WORK_ITEM}/" \
     -d "{\"state\": \"${DONE_STATE_ID}\"}"
   ```

6. **Report**

   `Closed Forgejo #<N>, moved Plane work item to Done.`

   If either API call failed, log the error and continue — archive is already in place. The user can clean up manually.

**Guardrails**

- Best-effort: never block the archive if APIs are unreachable. The archive is the durable artifact; tracker closure is a sidecar.
- Idempotent: closing an already-closed Forgejo issue is a no-op (Forgejo accepts the PATCH); moving a Plane work item to its current state is a no-op.
- Never modify the archived `proposal.md` — once a change is archived, its files are immutable history.
- This skill is project-level and custom-named. `openspec update` will NOT touch it. That's the point.

**Migration path**

When the homelab MCP gains state-transition tools:

```
mcp__homelab__forgejo_close_issue(repo="intellij-openspec", number=<N>, comment="...")
mcp__homelab__plane_move_card(project="OSPEC", id=<UUID>, state="Done")
```

Replace steps 4–5 with these MCP calls and drop the `scripts/.env` dependency. Track this as a follow-up against `johnb/homelab-admin` when the close-flow friction is felt enough to justify the MCP work.