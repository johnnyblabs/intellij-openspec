---
name: "OPSX: Archive"
description: Archive a completed change in the experimental workflow
category: Workflow
tags: [workflow, archive, experimental]
---

Archive a completed change in the experimental workflow.

**Input**: Optionally specify a change name after `/opsx:archive` (e.g., `/opsx:archive add-auth`). If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

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
   - Prompt user for confirmation to continue
   - Proceed if user confirms

3. **Check task completion status**

   Read the tasks file (typically `tasks.md`) to check for incomplete tasks.

   Count tasks marked with `- [ ]` (incomplete) vs `- [x]` (complete).

   **If incomplete tasks found:**
   - Display warning showing count of incomplete tasks
   - Prompt user for confirmation to continue
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

6. **Display summary**

   Show archive completion summary including:
   - Change name
   - Schema that was used
   - Archive location
   - Spec sync status (synced / sync skipped / no delta specs)
   - Note about any warnings (incomplete artifacts/tasks)

**Output On Success**

```
## Archive Complete

**Change:** <change-name>
**Schema:** <schema-name>
**Archived to:** openspec/changes/archive/YYYY-MM-DD-<name>/
**Specs:** ✓ Synced to main specs

All artifacts complete. All tasks complete.
```

**Output On Success (No Delta Specs)**

```
## Archive Complete

**Change:** <change-name>
**Schema:** <schema-name>
**Archived to:** openspec/changes/archive/YYYY-MM-DD-<name>/
**Specs:** No delta specs

All artifacts complete. All tasks complete.
```

**Output On Success With Warnings**

```
## Archive Complete (with warnings)

**Change:** <change-name>
**Schema:** <schema-name>
**Archived to:** openspec/changes/archive/YYYY-MM-DD-<name>/
**Specs:** Sync skipped (user chose to skip)

**Warnings:**
- Archived with 2 incomplete artifacts
- Archived with 3 incomplete tasks
- Delta spec sync was skipped (user chose to skip)

Review the archive if this was not intentional.
```

**Output On Error (Archive Exists)**

```
## Archive Failed

**Change:** <change-name>
**Target:** openspec/changes/archive/YYYY-MM-DD-<name>/

Target archive directory already exists.

**Options:**
1. Rename the existing archive
2. Delete the existing archive if it's a duplicate
3. Wait until a different date to archive
```

7. **Post-archive: commit, push, update trackers**

   After displaying the archive summary, perform these steps automatically:

   a. **Commit** all changes (implementation code, archived change directory, synced specs) and **push** to the remote repository.

   b. **Close Forgejo issue**: Load credentials from `scripts/.env` (`FORGEJO_TOKEN`, `FORGEJO_URL`). Search for a matching open issue by title:
      ```bash
      curl -s -H "Authorization: token $FORGEJO_TOKEN" \
        "$FORGEJO_URL/api/v1/repos/johnb/OpenSpecPlugin/issues?type=issues&state=open&limit=50" \
        | jq -r '.[] | "\(.number)\t\(.title)"'
      ```
      If a match is found, add a completion comment and close it:
      ```bash
      curl -s -X POST -H "Authorization: token $FORGEJO_TOKEN" -H "Content-Type: application/json" \
        "$FORGEJO_URL/api/v1/repos/johnb/OpenSpecPlugin/issues/<number>/comments" \
        -d '{"body": "Completed. <brief summary of what was done>"}'
      curl -s -X PATCH -H "Authorization: token $FORGEJO_TOKEN" -H "Content-Type: application/json" \
        "$FORGEJO_URL/api/v1/repos/johnb/OpenSpecPlugin/issues/<number>" \
        -d '{"state": "closed"}'
      ```

   c. **Update Plane work item**: Load credentials from `scripts/.env` (`PLANE_API_KEY`, `PLANE_URL`, `PLANE_WORKSPACE`). Find the project ID, then search for a matching work item by title:
      ```bash
      PLANE_PID=$(curl -s -H "X-API-Key: $PLANE_API_KEY" \
        "$PLANE_URL/api/v1/workspaces/$PLANE_WORKSPACE/projects/" \
        | jq -r '(.results[]? // .[]?) | select(.name == "OpenSpec Plugin") | .id')
      curl -s -H "X-API-Key: $PLANE_API_KEY" \
        "$PLANE_URL/api/v1/workspaces/$PLANE_WORKSPACE/projects/$PLANE_PID/work-items/?per_page=200" \
        | jq -r '(.results[]? // .[]?) | "\(.id)\t\(.name)"'
      ```
      If a match is found, get the "Done" state ID and update:
      ```bash
      DONE_STATE=$(curl -s -H "X-API-Key: $PLANE_API_KEY" \
        "$PLANE_URL/api/v1/workspaces/$PLANE_WORKSPACE/projects/$PLANE_PID/states/" \
        | jq -r '(.results[]? // .[]?) | select(.name == "Done") | .id')
      curl -s -X PATCH -H "X-API-Key: $PLANE_API_KEY" -H "Content-Type: application/json" \
        "$PLANE_URL/api/v1/workspaces/$PLANE_WORKSPACE/projects/$PLANE_PID/work-items/<item-id>/" \
        -d "{\"state\": \"$DONE_STATE\"}"
      ```

   d. **Cross-link Plane and Forgejo**: If both a Forgejo issue and Plane work item were found, link them by setting `external_id` on the Plane work item:
      ```bash
      curl -s -X PATCH -H "X-API-Key: $PLANE_API_KEY" -H "Content-Type: application/json" \
        "$PLANE_URL/api/v1/workspaces/$PLANE_WORKSPACE/projects/$PLANE_PID/work-items/<item-id>/" \
        -d '{"external_id": "forgejo-issue-<number>", "external_source": "forgejo"}'
      ```
      Skip if the work item already has an `external_id` set.

   e. If no matching issue or work item is found, skip that tracker update silently.

**Guardrails**
- Always prompt for change selection if not provided
- Use artifact graph (openspec status --json) for completion checking
- Don't block archive on warnings - just inform and confirm
- Preserve .openspec.yaml when moving to archive (it moves with the directory)
- Show clear summary of what happened
- If sync is requested, use the Skill tool to invoke `openspec-sync-specs` (agent-driven)
- If delta specs exist, always run the sync assessment and show the combined summary before prompting
- Post-archive steps (commit, push, tracker updates) are MANDATORY — do not skip unless the user explicitly says to
