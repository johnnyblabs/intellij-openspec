# Troubleshooting

## CLI Not Found

**Symptom:** Status bar shows "built-in mode", CLI commands fall back to built-in behavior.

**Causes and fixes:**

### macOS PATH Issue
macOS GUI apps don't inherit shell PATH from `.zshrc` or `.bash_profile`.

**Fix:** Set the CLI path explicitly in **Settings → Tools → OpenSpec → CLI Path**.

Find the CLI path:
```bash
which openspec
# or
npm root -g
# then: <global-root>/openspec-dev/bin/openspec
```

### CLI Not Installed
```bash
npm install -g openspec-dev
```

### Detection Cascade
The plugin tries these locations in order:
1. Settings-configured path
2. `openspec` on PATH
3. Login shell resolution (`/bin/zsh -l -c "which openspec"`)
4. Common paths: `/usr/local/bin/openspec`, `~/.npm-global/bin/openspec`

## Tree Shows Stale Data

**Symptom:** After editing files, the tree doesn't update.

**Fixes:**
1. Click **Refresh** in the toolbar
2. Enable **Settings → Tools → OpenSpec → Auto-refresh**
3. If files were modified outside IntelliJ, use **File → Synchronize** (or press ⌘⇧A → "Synchronize") to trigger VFS refresh

**Why:** IntelliJ's Virtual File System (VFS) may not detect external changes immediately.

## "Generate All" Grayed Out

**Causes:**
1. No AI provider selected — set one in **Settings → Tools → OpenSpec**
2. No API key stored — use the "Test / Store API Key" button in settings
3. No change selected — select a change node in the tree first

## API Call Fails

**Symptom:** Error notification with HTTP status code.

| Code | Meaning | Fix |
|------|---------|-----|
| 401 | Invalid API key | Re-enter key in Settings |
| 403 | Insufficient permissions | Check API key scopes/plan |
| 429 | Rate limited | Wait and retry |
| 500+ | Provider outage | Check provider status page |

Check the **Console** tab for full error details.

## Validation Shows Unexpected Warnings

- **RFC 2119 keywords:** Requirements should use MUST, SHOULD, MAY, SHALL, etc.
- **Strict mode:** Check if strict validation is enabled in settings
- **Schema mismatch:** Ensure `schema` in config.yaml matches your content structure

## Init Creates Wrong Structure

If the scaffolded structure looks different than expected:
- Check the **version** in config.yaml — different versions have different required artifacts
- Check the **profile** setting — profiles may customize templates

## Plugin Not Visible

1. Verify installation: **Settings → Plugins → Installed** → search "OpenSpec"
2. Check IDE version: requires IntelliJ 2024.1+
3. Check Java version: requires JDK 17+
4. Look in **Help → Show Log in Finder** for plugin loading errors

## No Auto-Refresh

1. Ensure **Auto-refresh** is enabled in Settings → Tools → OpenSpec
2. Only changes inside the `openspec/` directory trigger refresh
3. Refresh is debounced (300ms delay) — rapid changes may batch

---

**Previous:** [[Workflow-Patterns]] | **Next:** [[Architecture-Overview]]
