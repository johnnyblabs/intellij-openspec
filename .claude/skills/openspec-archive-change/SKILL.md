---
name: openspec-archive-change
description: Archive a completed change in the experimental workflow. Use when the user wants to finalize and archive a change after implementation is complete.
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.2.0"
---

Archive a completed change in the experimental workflow.

**Input**: Optionally specify a change name. If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

**Steps**

1. **If no change name provided, prompt for selection**

   Run `openspec list --json` to get available changes. Use the **AskUserQuestion tool** to let the user select.

   Show only active changes (not already archived).
   Include the schema used for each change if available.

   **IMPORTANT**: Do NOT guess or auto-select a change. Always let the user choose.

2. **Check artifact completion status**

   Run `openspec status --change "<name>" --json` to check artifact completion.

   Parse the JSON to understand:
   - `schemaName`: The workflow being used
   - `artifacts`: List of artifacts with their status (`done` or other)

   **If any artifacts are not `done`:**
   - Display warning listing incomplete artifacts
   - Use **AskUserQuestion tool** to confirm user wants to proceed
   - Proceed if user confirms

3. **Check task completion status**

   Read the tasks file (typically `tasks.md`) to check for incomplete tasks.

   Count tasks marked with `- [ ]` (incomplete) vs `- [x]` (complete).

   **If incomplete tasks found:**
   - Display warning showing count of incomplete tasks
   - Use **AskUserQuestion tool** to confirm user wants to proceed
   - Proceed if user confirms

   **If no tasks file exists:** Proceed without task-related warning.

4. **Assess delta spec sync state**

   Check for delta specs at `openspec/changes/<name>/specs/`. If none exist, proceed without sync prompt.

   **If delta specs exist:**
   - Compare each delta spec with its corresponding main spec at `openspec/specs/<capability>/spec.md`
   - Determine what changes would be applied (adds, modifications, removals, renames)
   - Show a combined summary before prompting

   **Prompt options:**
   - If changes needed: "Sync now (recommended)", "Archive without syncing"
   - If already synced: "Archive now", "Sync anyway", "Cancel"

   If user chooses sync, use Task tool (subagent_type: "general-purpose", prompt: "Use Skill tool to invoke openspec-sync-specs for change '<name>'. Delta spec analysis: <include the analyzed delta spec summary>"). Proceed to archive regardless of choice.

5. **Perform the archive**

   Create the archive directory if it doesn't exist:
   ```bash
   mkdir -p openspec/changes/archive
   ```

   Generate target name using current date: `YYYY-MM-DD-<change-name>`

   **Check if target already exists:**
   - If yes: Fail with error, suggest renaming existing archive or using different date
   - If no: Move the change directory to archive

   ```bash
   mv openspec/changes/<name> openspec/changes/archive/YYYY-MM-DD-<name>
   ```

6. **Close tracker items (Forgejo issue + Plane work item)**

   After archiving, auto-close tracker items if tracking metadata exists.

   a. **Parse tracker IDs from the archived `proposal.md`** — extract from the `## References` section. Tracker IDs live in `proposal.md` per OpenSpec community convention; do NOT read `.openspec.yaml`, whose schema doesn't carry tracking data:
      ```bash
      PROPOSAL=openspec/changes/archive/YYYY-MM-DD-<name>/proposal.md
      FORGEJO_ISSUE=$(grep -oE 'Forgejo:\s*[^#]+#[0-9]+' "$PROPOSAL" | grep -oE '[0-9]+$' | head -1)
      PLANE_WORK_ITEM=$(grep -oE 'Plane:[^`]*`[a-f0-9-]{36}`' "$PROPOSAL" | grep -oE '[a-f0-9-]{36}' | head -1)
      ```

      For backwards compatibility with pre-References changes, if no `## References` section is present, also check the legacy `.openspec.yaml` for an embedded `tracking:` block — log a one-line note that the change predates the convention.

   b. **If no tracker IDs are found**, skip this step silently.

   c. **Check for credentials**:
      ```bash
      test -f scripts/.env && source scripts/.env
      ```
      If missing, log "Tracker closure skipped — credentials not found" and skip.

   d. **Close Forgejo issue**:
      ```bash
      source scripts/.env
      FORGEJO_API="${FORGEJO_URL}/api/v1/repos/johnb/intellij-openspec"

      # Close the issue
      curl -s -X PATCH \
        -H "Authorization: token ${FORGEJO_TOKEN}" \
        -H "Content-Type: application/json" \
        "${FORGEJO_API}/issues/<ISSUE_NUMBER>" \
        -d '{"state": "closed"}'

      # Add completion comment
      curl -s -X POST \
        -H "Authorization: token ${FORGEJO_TOKEN}" \
        -H "Content-Type: application/json" \
        "${FORGEJO_API}/issues/<ISSUE_NUMBER>/comments" \
        -d "$(python3 -c "import json; print(json.dumps({'body': 'Archived in change `<change-name>`. Version target: <current version from build.gradle.kts>.'}))")"
      ```

   e. **Move Plane work item to Done**:
      ```bash
      PLANE_PROJECT_ID="d358203d-16dd-48c4-ba22-f82be6781dd2"
      PLANE_WS_API="${PLANE_URL}/api/v1/workspaces/${PLANE_WORKSPACE}/projects/${PLANE_PROJECT_ID}"

      # Get Done state ID
      DONE_STATE_ID=$(curl -s -H "X-API-Key: ${PLANE_API_KEY}" \
        "${PLANE_WS_API}/states/" | \
        python3 -c "import sys,json; r=json.load(sys.stdin); states=r.get('results',r); print(next((s['id'] for s in states if s['name']=='Done'), ''))")

      # Update work item state
      curl -s -X PATCH \
        -H "X-API-Key: ${PLANE_API_KEY}" \
        -H "Content-Type: application/json" \
        "${PLANE_WS_API}/work-items/<WORK_ITEM_ID>/" \
        -d "{\"state\": \"${DONE_STATE_ID}\"}"
      ```

   f. **Report**: "Closed Forgejo #<N>, moved Plane work item to Done."
      If API calls fail, log the error and continue — archive is already done.

7. **Display summary**

   Show archive completion summary including:
   - Change name
   - Schema that was used
   - Archive location
   - Whether specs were synced (if applicable)
   - Tracker closure status (closed/skipped)
   - Note about any warnings (incomplete artifacts/tasks)

**Output On Success**

```
## Archive Complete

**Change:** <change-name>
**Schema:** <schema-name>
**Archived to:** openspec/changes/archive/YYYY-MM-DD-<name>/
**Specs:** ✓ Synced to main specs (or "No delta specs" or "Sync skipped")
**Trackers:** ✓ Forgejo #<N> closed, Plane work item Done (or "No tracking metadata" or "Skipped")

All artifacts complete. All tasks complete.
```

**Guardrails**
- Always prompt for change selection if not provided
- Use artifact graph (openspec status --json) for completion checking
- Don't block archive on warnings - just inform and confirm
- Preserve .openspec.yaml when moving to archive (it moves with the directory)
- Show clear summary of what happened
- If sync is requested, use openspec-sync-specs approach (agent-driven)
- If delta specs exist, always run the sync assessment and show the combined summary before prompting
- Tracker closure is best-effort — never fail the archive if APIs are unreachable
