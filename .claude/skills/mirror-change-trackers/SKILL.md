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
   ```

   Pull the first 1–3 lines of the **## Why** or **## What** section for the Forgejo issue body. Just enough that someone glancing at the Forgejo issue knows what the change is about; the proposal itself is the source of truth.

   **If `## References` already exists** with a `Forgejo:` line: the change is already mirrored. Report the existing IDs and stop — do not double-mirror.

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

6. **Append References to proposal.md**

   Append (do not overwrite) at the bottom of `proposal.md`:

   ```markdown

   ## References

   - GitHub: <org>/<repo>#<num>     ← only if detected in step 3
   - Forgejo: johnb/intellij-openspec#<ISSUE_NUMBER>
   - Plane: openspec/issue/<PLANE_IDENTIFIER> (`<PLANE_UUID>`)
   ```

   This is the OpenSpec community convention. Tracker IDs live in `proposal.md`, never in `.openspec.yaml` (its Zod schema only accepts `schema:` + `created:` and silently strips unknown keys).

7. **Report**

   One line: `Tracked as Forgejo #<N> / Plane <IDENTIFIER>.`

   If anything was partial (Forgejo open but Plane error), say so explicitly.

**Guardrails**

- Never double-mirror — if `## References` already has a `Forgejo:` line, stop with a report of the existing IDs.
- Never modify `.openspec.yaml` — its schema strips unknown keys silently.
- The MCP call replaces the entire inline curl plumbing that used to be in the openspec-* skills. If the MCP server is unreachable (no homelab on the network, server crashed), report "Tracker mirror skipped — homelab MCP unreachable" and exit cleanly. The change itself is still valid; the mirror is a sidecar.
- This skill is project-level and custom-named. `openspec update` will NOT touch it. That's the point.