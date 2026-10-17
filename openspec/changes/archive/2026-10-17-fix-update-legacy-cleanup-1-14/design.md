## Context

See proposal.md, Why. `UpdateOutputParser.parseLegacyCleanup` finds the `Files to remove` header and collects the bullet run. Before the first bullet, it tolerates only blank lines and a line starting with `No user content to preserve`, and anything else ends the parse with nothing. `UpdateLegacyCleanupService.handleUpdateResult` turns the paths into a notification ("…legacy file(s) it reports safe to remove…"). `LegacyCleanupDialog` shows every path ticked and quotes "No user content to preserve".

Real `openspec update` output was captured from CLI 1.13.2 and 1.14.3 on the same project: `init --tools cursor`, then three legacy `.cursor/commands/openspec-*.md` files, then `update`. The two outputs are identical except for the guidance line. The existing 1.6.0 and 1.7.0 captures use Junie and the old line.

## Goals / Non-Goals

**Goals:** recognize the block on every supported CLI; never claim a safety guarantee the CLI didn't make; exercise the flow again in the demo, the walkthrough and uiSmoke.

**Non-Goals:** changing the deletion mechanics (consent, VFS write command, project-root check, re-running update) or the escape hatches.

## Decisions

### D1. Tolerate any guidance line and return it

The parser returns a small record: the paths plus the guidance line, the first non-blank line between the header and the bullets, or null if there is none. Any non-bullet lines before the first bullet are allowed. The block still ends at the first non-bullet line after the bullets start, and a missing header or an empty bullet run still yields nothing. `parseLegacyCleanup(String)` stays as a thin wrapper that returns the paths, for existing callers and tests.

*Alternative considered:* whitelisting both known wordings. That breaks again on the next rewording, and these structural markers were always meant to be the key.

### D2. Default tick state follows the CLI's own statement

The dialog starts the boxes ticked only when the guidance line certifies there's no user content (it starts with "No user content to preserve"). Otherwise, including the 1.14+ "Back up any custom content" line and a missing line, they start unticked. The dialog quotes the line exactly as the CLI printed it. The notification says how many legacy files the CLI listed for removal and asks the user to review them, with no claim that they're safe.

*Alternative considered:* keeping them all ticked. That would contradict the CLI's own warning on 1.14, and deletion through the dialog is undoable, but only for one step.

### D3. Exercise the flow on current CLIs

`scripts/seed-lifecycle-demo.sh` adds legacy `.cursor/commands/openspec-*.md` files, initializing with Cursor as a second tool, so `openspec update` reports a cleanup block on CLI 1.8 and later. The Junie seeding stays for the AI-tool detection stops.

The uiSmoke journey's `< 1.8` skip and its incorrect comment are removed, and its committed fixture is regenerated from the script. The walkthrough's Stop 3 and the skill's intro go back to describing the full flow, with the 1.14 wording.

## Risks / Trade-offs

- **Regenerating the uiSmoke fixture changes the demo for every journey.** → The other journeys don't use `.cursor/`. Running `uiSmoke` locally under `caffeinate` after regenerating is part of the verify group.
- **A future CLI moves the guidance line below the bullets.** → The bullets would still parse and the line would be null, so files start unticked. That's the safe default.

## Migration Plan

Code and fixtures only. To roll back, revert the commit.
