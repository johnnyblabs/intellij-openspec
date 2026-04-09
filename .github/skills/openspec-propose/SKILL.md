---
name: openspec-propose
description: Propose a new change with all artifacts generated in one step. Use when the user wants to quickly describe what they want to build and get a complete proposal with design, specs, and tasks ready for implementation.
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.2.0"
---

Propose a new change - create the change and generate all artifacts in one step.

I'll create a change with artifacts:
- proposal.md (what & why)
- design.md (how)
- tasks.md (implementation steps)

When ready to implement, run /opsx:apply

---

**Input**: The user's request should include a change name (kebab-case) OR a description of what they want to build.

**Steps**

1. **If no clear input provided, ask what they want to build**

   Use the **AskUserQuestion tool** (open-ended, no preset options) to ask:
   > "What change do you want to work on? Describe what you want to build or fix."

   From their description, derive a kebab-case name (e.g., "add user authentication" → `add-user-auth`).

   **IMPORTANT**: Do NOT proceed without understanding what the user wants to build.

2. **Create the change directory**
   ```bash
   openspec new change "<name>"
   ```
   This creates a scaffolded change at `openspec/changes/<name>/` with `.openspec.yaml`.

3. **Get the artifact build order**
   ```bash
   openspec status --change "<name>" --json
   ```
   Parse the JSON to get:
   - `applyRequires`: array of artifact IDs needed before implementation (e.g., `["tasks"]`)
   - `artifacts`: list of all artifacts with their status and dependencies

4. **Create artifacts in sequence until apply-ready**

   Use the **TodoWrite tool** to track progress through the artifacts.

   Loop through artifacts in dependency order (artifacts with no pending dependencies first):

   a. **For each artifact that is `ready` (dependencies satisfied)**:
      - Get instructions:
        ```bash
        openspec instructions <artifact-id> --change "<name>" --json
        ```
      - The instructions JSON includes:
        - `context`: Project background (constraints for you - do NOT include in output)
        - `rules`: Artifact-specific rules (constraints for you - do NOT include in output)
        - `template`: The structure to use for your output file
        - `instruction`: Schema-specific guidance for this artifact type
        - `outputPath`: Where to write the artifact
        - `dependencies`: Completed artifacts to read for context
      - Read any completed dependency files for context
      - Create the artifact file using `template` as the structure
      - Apply `context` and `rules` as constraints - but do NOT copy them into the file
      - Show brief progress: "Created <artifact-id>"

   b. **Continue until all `applyRequires` artifacts are complete**
      - After creating each artifact, re-run `openspec status --change "<name>" --json`
      - Check if every artifact ID in `applyRequires` has `status: "done"` in the artifacts array
      - Stop when all `applyRequires` artifacts are done

   c. **If an artifact requires user input** (unclear context):
      - Use **AskUserQuestion tool** to clarify
      - Then continue with creation

5. **Show final status**
   ```bash
   openspec status --change "<name>"
   ```

**Output**

After completing all artifacts, summarize:
- Change name and location
- List of artifacts created with brief descriptions
- What's ready: "All artifacts created! Ready for implementation."
- Prompt: "Run `/opsx:apply` or ask me to implement to start working on the tasks."

**Artifact Creation Guidelines**

- Follow the `instruction` field from `openspec instructions` for each artifact type
- The schema defines what each artifact should contain - follow it
- Read dependency artifacts for context before creating new ones
- Use `template` as the structure for your output file - fill in its sections
- **IMPORTANT**: `context` and `rules` are constraints for YOU, not content for the file
  - Do NOT copy `<context>`, `<rules>`, `<project_context>` blocks into the artifact
  - These guide what you write, but should never appear in the output

6. **Create tracker items (Forgejo issue + Plane work item)**

   After all artifacts are created, auto-create tracker items if credentials are available.

   a. **Check for credentials**:
      ```bash
      test -f scripts/.env && source scripts/.env
      ```
      If `scripts/.env` doesn't exist or `FORGEJO_TOKEN` / `PLANE_API_KEY` are empty, log "Tracker integration skipped — credentials not found" and skip this step.

   b. **Infer labels and priority** from the change name and proposal:
      - Bug keywords (fix, bug, deadlock, crash, violation): Forgejo label `bug` (id from API), Plane priority `urgent` or `high`
      - Infrastructure keywords (ci, pipeline, build, release, changelog): Forgejo label `infrastructure`, Plane priority `high`
      - Default: Forgejo label `enhancement`, Plane priority `medium`

   c. **Create Forgejo issue**:
      ```bash
      source scripts/.env
      FORGEJO_API="${FORGEJO_URL}/api/v1/repos/johnb/intellij-openspec"

      # Get label ID for the inferred label name
      LABEL_ID=$(curl -s -H "Authorization: token ${FORGEJO_TOKEN}" \
        "${FORGEJO_API}/labels?limit=50" | \
        python3 -c "import sys,json; labels=json.load(sys.stdin); print(next((l['id'] for l in labels if l['name']=='<LABEL>'), ''))")

      # Get milestone ID for catch-all milestone (v0.3.0 or current)
      MILESTONE_ID=$(curl -s -H "Authorization: token ${FORGEJO_TOKEN}" \
        "${FORGEJO_API}/milestones?limit=10" | \
        python3 -c "import sys,json; ms=json.load(sys.stdin); print(next((m['id'] for m in ms if 'v0.3' in m['title']), ''))")

      # Create the issue
      ISSUE_RESPONSE=$(curl -s -X POST \
        -H "Authorization: token ${FORGEJO_TOKEN}" \
        -H "Content-Type: application/json" \
        "${FORGEJO_API}/issues" \
        -d "$(python3 -c "import json; print(json.dumps({
          'title': '<change-name>',
          'body': '<first 2-3 lines of proposal + link to Plane work item>',
          'labels': [<LABEL_ID>],
          'milestone': <MILESTONE_ID>
        }))")")

      ISSUE_NUMBER=$(echo "$ISSUE_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('number',''))")
      ```

   d. **Create Plane work item**:
      ```bash
      PLANE_PROJECT_ID="d358203d-16dd-48c4-ba22-f82be6781dd2"
      PLANE_WS_API="${PLANE_URL}/api/v1/workspaces/${PLANE_WORKSPACE}/projects/${PLANE_PROJECT_ID}"

      # Get label ID for inferred label
      PLANE_LABEL_ID=$(curl -s -H "X-API-Key: ${PLANE_API_KEY}" \
        "${PLANE_WS_API}/labels/" | \
        python3 -c "import sys,json; r=json.load(sys.stdin); labels=r.get('results',r); print(next((l['id'] for l in labels if l['name']=='<LABEL>'), ''))")

      # Get cycle ID for catch-all cycle (v0.3.0)
      CYCLE_ID=$(curl -s -H "X-API-Key: ${PLANE_API_KEY}" \
        "${PLANE_WS_API}/cycles/" | \
        python3 -c "import sys,json; r=json.load(sys.stdin); cycles=r.get('results',r); print(next((c['id'] for c in cycles if 'v0.3' in c['name']), ''))")

      # Create work item
      WORK_ITEM_RESPONSE=$(curl -s -X POST \
        -H "X-API-Key: ${PLANE_API_KEY}" \
        -H "Content-Type: application/json" \
        "${PLANE_WS_API}/work-items/" \
        -d "$(python3 -c "import json; print(json.dumps({
          'name': '<change-name>',
          'description_html': '<p><summary from proposal>. Forgejo #<ISSUE_NUMBER>.</p>',
          'labels': ['<PLANE_LABEL_ID>'],
          'priority': '<PRIORITY>'
        }))")")

      WORK_ITEM_ID=$(echo "$WORK_ITEM_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))")

      # Add to cycle
      curl -s -X POST \
        -H "X-API-Key: ${PLANE_API_KEY}" \
        -H "Content-Type: application/json" \
        "${PLANE_WS_API}/cycles/${CYCLE_ID}/cycle-issues/" \
        -d "{\"issues\": [\"${WORK_ITEM_ID}\"]}"
      ```

   e. **Write tracking metadata to `.openspec.yaml`**:
      Append to the change's `.openspec.yaml`:
      ```yaml
      tracking:
        forgejo_issue: <ISSUE_NUMBER>
        plane_work_item: <WORK_ITEM_ID>
      ```

   f. **Report**: Include in the final output summary:
      ```
      Tracked as Forgejo #<N> / Plane <WORK_ITEM_ID>.
      ```
      If any API call fails, log the error and continue — tracker creation is best-effort.

**Guardrails**
- Create ALL artifacts needed for implementation (as defined by schema's `apply.requires`)
- Always read dependency artifacts before creating a new one
- If context is critically unclear, ask the user - but prefer making reasonable decisions to keep momentum
- If a change with that name already exists, ask if user wants to continue it or create a new one
- Verify each artifact file exists after writing before proceeding to next
- Tracker creation is best-effort — never fail the change creation if APIs are unreachable
