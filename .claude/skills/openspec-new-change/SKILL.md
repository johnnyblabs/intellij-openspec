---
name: openspec-new-change
description: Start a new OpenSpec change using the experimental artifact workflow. Use when the user wants to create a new feature, fix, or modification with a structured step-by-step approach.
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.2.0"
---

Start a new change using the experimental artifact-driven approach.

**Input**: The user's request should include a change name (kebab-case) OR a description of what they want to build.

**Steps**

1. **If no clear input provided, ask what they want to build**

   Use the **AskUserQuestion tool** (open-ended, no preset options) to ask:
   > "What change do you want to work on? Describe what you want to build or fix."

   From their description, derive a kebab-case name (e.g., "add user authentication" → `add-user-auth`).

   **IMPORTANT**: Do NOT proceed without understanding what the user wants to build.

2. **Determine the workflow schema**

   Use the default schema (omit `--schema`) unless the user explicitly requests a different workflow.

   **Use a different schema only if the user mentions:**
   - A specific schema name → use `--schema <name>`
   - "show workflows" or "what workflows" → run `openspec schemas --json` and let them choose

   **Otherwise**: Omit `--schema` to use the default.

3. **Create the change directory**
   ```bash
   openspec new change "<name>"
   ```
   Add `--schema <name>` only if the user requested a specific workflow.
   This creates a scaffolded change at `openspec/changes/<name>/` with the selected schema.

4. **Show the artifact status**
   ```bash
   openspec status --change "<name>"
   ```
   This shows which artifacts need to be created and which are ready (dependencies satisfied).

5. **Get instructions for the first artifact**
   The first artifact depends on the schema (e.g., `proposal` for spec-driven).
   Check the status output to find the first artifact with status "ready".
   ```bash
   openspec instructions <first-artifact-id> --change "<name>"
   ```
   This outputs the template and context for creating the first artifact.

6. **STOP and wait for user direction**

**Output**

After completing the steps, summarize:
- Change name and location
- Schema/workflow being used and its artifact sequence
- Current status (0/N artifacts complete)
- The template for the first artifact
- Prompt: "Ready to create the first artifact? Just describe what this change is about and I'll draft it, or ask me to continue."

7. **Create tracker items (Forgejo issue + Plane work item)**

   After showing the status, auto-create tracker items if credentials are available.

   a. **Check for credentials**:
      ```bash
      test -f scripts/.env && source scripts/.env
      ```
      If `scripts/.env` doesn't exist or `FORGEJO_TOKEN` / `PLANE_API_KEY` are empty, log "Tracker integration skipped — credentials not found" and skip this step.

   b. **Infer labels and priority** from the change name:
      - Bug keywords (fix, bug, deadlock, crash, violation): Forgejo label `bug`, Plane priority `high`
      - Infrastructure keywords (ci, pipeline, build, release, changelog): Forgejo label `infrastructure`, Plane priority `high`
      - Default: Forgejo label `enhancement`, Plane priority `medium`

   c. **Create Forgejo issue**:
      ```bash
      source scripts/.env
      FORGEJO_API="${FORGEJO_URL}/api/v1/repos/johnb/intellij-openspec"
      LABEL_ID=$(curl -s -H "Authorization: token ${FORGEJO_TOKEN}" \
        "${FORGEJO_API}/labels?limit=50" | \
        python3 -c "import sys,json; labels=json.load(sys.stdin); print(next((l['id'] for l in labels if l['name']=='<LABEL>'), ''))")
      MILESTONE_ID=$(curl -s -H "Authorization: token ${FORGEJO_TOKEN}" \
        "${FORGEJO_API}/milestones?limit=10" | \
        python3 -c "import sys,json; ms=json.load(sys.stdin); print(next((m['id'] for m in ms if 'v0.3' in m['title']), ''))")
      ISSUE_RESPONSE=$(curl -s -X POST \
        -H "Authorization: token ${FORGEJO_TOKEN}" -H "Content-Type: application/json" \
        "${FORGEJO_API}/issues" \
        -d "$(python3 -c "import json; print(json.dumps({'title': '<change-name>', 'body': 'OpenSpec change: <change-name>', 'labels': [<LABEL_ID>], 'milestone': <MILESTONE_ID>}))")")
      ISSUE_NUMBER=$(echo "$ISSUE_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('number',''))")
      ```

   d. **Create Plane work item**:
      ```bash
      PLANE_PROJECT_ID="d358203d-16dd-48c4-ba22-f82be6781dd2"
      PLANE_WS_API="${PLANE_URL}/api/v1/workspaces/${PLANE_WORKSPACE}/projects/${PLANE_PROJECT_ID}"
      PLANE_LABEL_ID=$(curl -s -H "X-API-Key: ${PLANE_API_KEY}" \
        "${PLANE_WS_API}/labels/" | \
        python3 -c "import sys,json; r=json.load(sys.stdin); labels=r.get('results',r); print(next((l['id'] for l in labels if l['name']=='<LABEL>'), ''))")
      CYCLE_ID=$(curl -s -H "X-API-Key: ${PLANE_API_KEY}" \
        "${PLANE_WS_API}/cycles/" | \
        python3 -c "import sys,json; r=json.load(sys.stdin); cycles=r.get('results',r); print(next((c['id'] for c in cycles if 'v0.3' in c['name']), ''))")
      # John's Plane user id — required in `assignees` so the item appears in "Assigned to me" / "My Issues" views.
      # Hardcoded after first lookup. To re-resolve: GET /workspaces/${PLANE_WORKSPACE}/members/ and pick email=johnboyce@comcast.net.
      JOHN_PLANE_ID="70595e44-f801-45a2-b6da-cd3afc3b93ab"
      WORK_ITEM_RESPONSE=$(curl -s -X POST \
        -H "X-API-Key: ${PLANE_API_KEY}" -H "Content-Type: application/json" \
        "${PLANE_WS_API}/work-items/" \
        -d "$(python3 -c "import json; print(json.dumps({'name': '<change-name>', 'description_html': '<p>OpenSpec change. Forgejo #<ISSUE_NUMBER>.</p>', 'labels': ['<PLANE_LABEL_ID>'], 'priority': '<PRIORITY>', 'assignees': ['${JOHN_PLANE_ID}']}))")")
      WORK_ITEM_ID=$(echo "$WORK_ITEM_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))")
      curl -s -X POST -H "X-API-Key: ${PLANE_API_KEY}" -H "Content-Type: application/json" \
        "${PLANE_WS_API}/cycles/${CYCLE_ID}/cycle-issues/" -d "{\"issues\": [\"${WORK_ITEM_ID}\"]}"
      ```

   e. **Append References to `proposal.md`** — append a `## References` section at the bottom of the change's scaffolded `proposal.md`. This is the OpenSpec community convention for cross-linking trackers (do NOT touch `.openspec.yaml`; its upstream Zod schema only accepts `schema:` + `created:` and silently strips unknown keys, so embedding tracking data there is fragile).
      ```markdown

      ## References

      - Forgejo: johnb/intellij-openspec#<ISSUE_NUMBER>
      - Plane: openspec/issue/<SEQUENCE_ID> (`<WORK_ITEM_ID>`)
      ```
      If the change is fixing an upstream issue (e.g. GitHub), add a `- GitHub: <repo>#<num>` line above the Forgejo line.

   f. **Report**: Include in output: "Tracked as Forgejo #<N> / Plane <ID>."
      If API calls fail, log the error and continue.

**Guardrails**
- Do NOT create any artifacts yet - just show the instructions
- Do NOT advance beyond showing the first artifact template
- If the name is invalid (not kebab-case), ask for a valid name
- If a change with that name already exists, suggest continuing that change instead
- Pass --schema if using a non-default workflow
- Tracker creation is best-effort — never fail the change creation if APIs are unreachable
