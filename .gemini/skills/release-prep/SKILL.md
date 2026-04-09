---
name: release-prep
description: Pre-tag release checklist — validates version, changelog, build, archived changes, and tracker state before tagging. Auto-creates milestones/cycles and reassigns issues.
license: MIT
compatibility: Requires openspec CLI and scripts/.env for tracker integration.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.2.0"
---

Pre-tag release readiness checklist. Validates everything before you push a `v*` tag.

**Input**: Version number (e.g., `/release-prep v0.2.9` or `/release-prep 0.2.9`).

**Steps**

1. **Parse the version**

   Extract the semver from the input (strip leading `v` if present). Example: `v0.2.9` → `0.2.9`.

   If no version provided, read it from `build.gradle.kts`:
   ```bash
   grep '^version = ' build.gradle.kts
   ```

2. **Version validation**

   Check that `build.gradle.kts` contains `version = "<VERSION>"`.
   ```bash
   grep -q "version = \"<VERSION>\"" build.gradle.kts
   ```
   - Pass: "Version: <VERSION> in build.gradle.kts ✓"
   - Fail: "Version: build.gradle.kts has <ACTUAL>, expected <VERSION> ✗" — **BLOCKER**

3. **Changelog validation**

   Check that `CHANGELOG.md` has a header matching the version:
   ```bash
   grep -q "^## v<VERSION>" CHANGELOG.md
   ```
   - Pass: "Changelog: CHANGELOG.md has v<VERSION> entry ✓"
   - Fail: "Changelog: no v<VERSION> entry in CHANGELOG.md ✗" — **BLOCKER**

4. **Build verification**

   ```bash
   ./gradlew build
   ```
   - Pass: "Build: ./gradlew build passes ✓"
   - Fail: "Build: failed ✗" — **BLOCKER** (show last 10 lines of output)

5. **Active changes check**

   ```bash
   openspec list --json
   ```
   List any active (non-archived) changes. These are potential blockers — changes that should have been archived before releasing.
   - No active changes: "Changes: all archived ✓"
   - Active changes exist: List each with "✗ still active" — **BLOCKER**

6. **Forgejo milestone management**

   Check for credentials first:
   ```bash
   test -f scripts/.env && source scripts/.env
   ```
   If credentials missing, log "Tracker validation skipped — credentials not found" and skip steps 6-7.

   a. **Check if milestone exists** for this version:
      ```bash
      source scripts/.env
      FORGEJO_API="${FORGEJO_URL}/api/v1/repos/johnb/intellij-openspec"
      curl -s -H "Authorization: token ${FORGEJO_TOKEN}" \
        "${FORGEJO_API}/milestones?limit=20" | \
        python3 -c "import sys,json; ms=json.load(sys.stdin); matches=[m for m in ms if 'v<VERSION>' in m['title']]; print(matches[0]['id'] if matches else '')"
      ```

   b. **If milestone doesn't exist**, create it:
      ```bash
      curl -s -X POST \
        -H "Authorization: token ${FORGEJO_TOKEN}" \
        -H "Content-Type: application/json" \
        "${FORGEJO_API}/milestones" \
        -d "$(python3 -c "import json; print(json.dumps({'title': 'v<VERSION>', 'description': 'Release v<VERSION>'}))")"
      ```
      Report: "[creating Forgejo milestone v<VERSION>...]"

   c. **Find issues to reassign**: Look for closed issues that reference this version's changes (by checking archived change names against issue titles). Move them from the catch-all milestone to the release milestone:
      ```bash
      # For each matching issue, update its milestone
      curl -s -X PATCH \
        -H "Authorization: token ${FORGEJO_TOKEN}" \
        -H "Content-Type: application/json" \
        "${FORGEJO_API}/issues/<ISSUE_NUMBER>" \
        -d "{\"milestone\": <NEW_MILESTONE_ID>}"
      ```

   d. **Validate milestone issues**: List all issues in the release milestone and their open/closed status.
      - All closed: "Forgejo: all issues in v<VERSION> closed ✓"
      - Open issues: List them as **WARNINGS** (not blockers — some may span releases)

7. **Plane cycle management**

   a. **Check if cycle exists** for this version:
      ```bash
      PLANE_PROJECT_ID="d358203d-16dd-48c4-ba22-f82be6781dd2"
      PLANE_WS_API="${PLANE_URL}/api/v1/workspaces/${PLANE_WORKSPACE}/projects/${PLANE_PROJECT_ID}"
      curl -s -H "X-API-Key: ${PLANE_API_KEY}" \
        "${PLANE_WS_API}/cycles/" | \
        python3 -c "import sys,json; r=json.load(sys.stdin); cycles=r.get('results',r); matches=[c for c in cycles if 'v<VERSION>' in c['name']]; print(matches[0]['id'] if matches else '')"
      ```

   b. **If cycle doesn't exist**, create it:
      ```bash
      curl -s -X POST \
        -H "X-API-Key: ${PLANE_API_KEY}" \
        -H "Content-Type: application/json" \
        "${PLANE_WS_API}/cycles/" \
        -d "$(python3 -c "import json,datetime; today=datetime.date.today(); print(json.dumps({'name': 'v<VERSION>', 'description': 'Release v<VERSION>', 'start_date': str(today), 'end_date': str(today)}))")"
      ```
      Report: "[creating Plane cycle v<VERSION>...]"

   c. **Move relevant work items** from catch-all cycle to release cycle (match by archived change names).

   d. **Validate cycle work items**: List all work items in the release cycle and their state.
      - All Done: "Plane: all work items in v<VERSION> Done ✓"
      - Incomplete items: List them as **WARNINGS**

8. **Display readiness summary**

   Format:
   ```
   ## Release Readiness: v<VERSION>

   Version:     <VERSION> in build.gradle.kts <✓/✗>
   Changelog:   CHANGELOG.md has v<VERSION> entry <✓/✗>
   Build:       ./gradlew build <passes ✓ / failed ✗>

   Changes:
     <change-1>    archived ✓
     <change-2>    ✗ still active

   Forgejo:
     [created milestone v<VERSION> / milestone already exists]
     [moved #N, #M from v0.3.0 to v<VERSION>]
     #N <title>    closed ✓
     #M <title>    open ⚠ (warning)

   Plane:
     [created cycle v<VERSION> / cycle already exists]
     [moved N work items to v<VERSION> cycle]
     OSP-N <title>  Done ✓
     OSP-M <title>  In Progress ⚠ (warning)

   ---
   <N> blockers found. Fix before tagging.
   OR
   All clear. Tag and push:
     git tag v<VERSION> && git push origin v<VERSION>
   OR
   <N> warnings. Proceed with caution.
   Tag and push:
     git tag v<VERSION> && git push origin v<VERSION>
   ```

**Guardrails**
- Never auto-tag or auto-push — only show the command for the user to run
- Blockers (version mismatch, missing changelog, build failure, active changes) prevent the "all clear" message
- Warnings (open issues, incomplete work items) do NOT block — just inform
- Tracker operations are best-effort — skip gracefully if APIs are unreachable
- If the user provides no version, infer from build.gradle.kts
