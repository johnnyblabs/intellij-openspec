---
name: mirror-change-trackers
description: Mirror an OpenSpec change to Forgejo + Plane (OSPEC project) via the homelab MCP. Invoke after `openspec-propose` / `openspec-new-change` so the change has a tracker record. Appends a ## References section to proposal.md with the resulting IDs.
license: MIT
compatibility: Requires homelab MCP server (configured in .mcp.json) and a change directory under openspec/changes/<name>/.
---

Mirror an OpenSpec change to its tracker shadow on Forgejo + Plane in a single MCP call. Replaces the inline curl plumbing that used to live in `openspec-propose` Step 6 and `openspec-new-change` Step 7. The new path is durable across `openspec update` because this skill lives at a custom name OpenSpec doesn't manage.

**Input**: a change name (kebab-case). If omitted, infer from the most recently created directory under `openspec/changes/` (skip `archive/`), or ask the user.

**Steps**

1. **Resolve the change directory**

   Resolve to `openspec/changes/<name>/`. If it doesn't exist, ask the user for the correct name before proceeding. Don't auto-create.

2. **Read proposal.md for body content**

   ```bash
   PROPOSAL=openspec/changes/<name>/proposal.md
   TRACKING=openspec/changes/<name>/.tracking.yaml
   ```

   Pull the first 1–3 lines of the **## Why** or **## What** section for the Forgejo issue body. Just enough that someone glancing at the Forgejo issue knows what the change is about; the proposal itself is the source of truth.

   **If `.tracking.yaml` already exists** in the change directory: the change is already mirrored. Report the existing IDs and stop — do not double-mirror.

3. **Detect upstream GitHub reference (optional)**

   If `proposal.md` mentions `openspec/openspec#NN` or any other `<org>/<repo>#NN` style reference, capture it. It'll be added to the `## References` section as a `- GitHub:` line.

4. **Infer label and priority**

   From the change name (lowercased):

   | Keywords | Forgejo label | Plane priority |
   |---|---|---|
   | `fix`, `bug`, `deadlock`, `crash`, `violation` | `bug` | `high` |
   | `ci`, `pipeline`, `build`, `release`, `changelog` | `infrastructure` | `high` |
   | anything else | `enhancement` | `medium` |

   The user can override either by passing flags in their invocation (`label=bug`, `priority=urgent`), but defaults follow the table.

5. **Open Forgejo + Plane in one MCP call**

   ```
   mcp__homelab__forgejo_mirror_to_plane(
     repo="intellij-openspec",
     title="<change-name>",
     body_markdown="OpenSpec change: <change-name>.\n\n<1–3 line summary from proposal.md>",
     body_html="<p>OpenSpec change: <change-name>.</p><p><summary from proposal.md></p>",
     project="OSPEC",
     labels=["<inferred-label>"],
     state="Todo",
     priority="<inferred-priority>",
     target_date=None
   )
   ```

   The MCP tool handles all the gotchas internally:
   - Auto-assigns the Plane card to johnboyce (so it appears in "My Issues")
   - Resolves the per-project state UUID
   - Appends the Forgejo issue URL as a trailing `<p>` to the Plane card description

   Capture the response: `{"forgejo": {"number", "html_url"}, "plane": {"identifier", "id"}}`.

   **Failure handling:**
   - If `forgejo` returns `{"error": ...}`: report the error, stop. No Plane card was created.
   - If `forgejo` succeeded but `plane` returns `{"error": ...}`: report partial success, surface `forgejo_url_for_manual_link` so the user can manually create the Plane card. Still proceed to step 6 with just the Forgejo ID.

6. **Write `.tracking.yaml` sidecar**

   Write the tracker IDs to `openspec/changes/<name>/.tracking.yaml`. The file is gitignored — it stays local-only and never enters version control:

   ```yaml
   forgejo:
     repo: johnb/intellij-openspec
     issue: <ISSUE_NUMBER>
   plane:
     project: OSPEC
     identifier: <PLANE_IDENTIFIER>
     id: <PLANE_UUID>
   # optional — only if detected in step 3:
   github:
     ref: <org>/<repo>#<num>
   ```

   The sidecar travels with the change directory: when `openspec-archive-change` moves `openspec/changes/<name>/` to `openspec/changes/archive/YYYY-MM-DD-<name>/`, `.tracking.yaml` rides along. `close-change-trackers` reads it from the archived location.

   **Why a gitignored sidecar instead of `proposal.md` `## References`:** the repo pushes to a public GitHub mirror. Forgejo issue numbers and Plane UUIDs in `proposal.md` leak into every commit. The sidecar keeps the integration but isolates the leak. See CLAUDE.md "Tracker IDs go in a gitignored `.tracking.yaml` sidecar."

   **Why not `.openspec.yaml`:** its upstream Zod schema only accepts `schema:` + `created:` and silently strips unknown keys.

7. **Report**

   One line: `Tracked as Forgejo #<N> / Plane <IDENTIFIER>.`

   If anything was partial (Forgejo open but Plane error), say so explicitly.

**Guardrails**

- Never double-mirror — if `.tracking.yaml` already exists, stop with a report of the existing IDs.
- Never modify `.openspec.yaml` — its schema strips unknown keys silently.
- Never write tracker IDs into `proposal.md` — that file is published to the public GitHub mirror. Tracker IDs go in the gitignored sidecar only.
- The MCP call replaces the entire inline curl plumbing that used to be in the openspec-* skills. If the MCP server is unreachable, report "Tracker mirror skipped — homelab MCP unreachable" and exit cleanly. The change itself is still valid; the mirror is a sidecar.
- This skill is project-level and custom-named. `openspec update` will NOT touch it. That's the point.