# Workflow Profiles

OpenSpec ships with a **workflow profile system** (introduced in OpenSpec 1.2.0) that controls which workflow commands are installed for your AI tools. The plugin honors the active workflow profile by enabling or disabling its actions accordingly, and surfaces the active profile in several places so you always know what's available.

This page covers:

- The three things in OpenSpec that get called "profile" — and how they differ
- When to use the **core** profile vs **custom**
- The two-step process for changing your workflow profile
- How the plugin's UI surfaces (status bar widget, action suffix, Settings panel) work together

---

## Three concepts that often get confused

| Concept | Lives in | Holds | Per |
|---|---|---|---|
| **Schema** | `openspec/config.yaml` `schema:` field | e.g. `spec-driven` (the only one today; `openspec schema` is experimental) | Project |
| **Project profile** | `openspec/config.yaml` `profile:` block | name, description, language, framework, vendor — metadata for AI agents | Project |
| **Workflow profile** | Global CLI config (`~/.config/openspec/config.json`) | `core` or `custom` (preset selector) + a `workflows` array | User |

This page is about the **workflow profile**. The plugin's profile-aware action enablement, the status bar widget, and the Settings panel "Workflow profile" combo all concern this one.

The other two concepts are out of scope for this page — they're handled by `OpenSpecConfig` (project profile metadata) and the schema service (workflow methodology).

---

## Core vs custom — when to use which

The OpenSpec 1.2.0+ workflow profile system was introduced to **prevent skill bloat in AI context windows**. Every workflow command installed for your AI tool (Cursor, Claude Code, GitHub Copilot, etc.) consumes context. Core ships only what most users need; custom lets you opt in to extras.

### Core (default) — 5 essential workflows

```
propose · explore · apply · sync · archive
```

Core covers the full propose → apply → archive loop. If you're new to OpenSpec, or your work doesn't involve fast-forwarding through artifacts, verifying implementations against specs, or bulk-archiving completed changes, **core is everything you need**.

### Custom — opt in to expanded workflows

`custom` is the profile name the CLI reports whenever your workflow set diverges from any named preset. You reach it not by picking it from a dropdown, but by running the CLI's interactive picker — either via the **"Customize workflows…"** button in Settings (described below) or directly via `openspec config profile` in a terminal. The picker lets you toggle any subset of the expanded workflows on top of core:

| Workflow | What it does |
|---|---|
| `new` | One-step change creation that skips the full proposal artifact pipeline |
| `continue` | Continue an in-progress change by creating its next artifact |
| `ff` | Fast-forward through artifact creation in one go |
| `verify` | Validate that an implementation matches the original spec |
| `bulk-archive` | Archive multiple completed changes at once |
| `onboard` | Generate initial specs from an existing codebase |

**Switch to custom when** you find yourself wanting any of the above — typically once you're comfortable with the core loop and want power-user shortcuts. It's not an upgrade; it's a different scope. Many users run on core indefinitely.

The plugin keeps `custom` deliberately out of the Settings combo because there's no meaningful "default custom" to pick — every custom set is the user's. Choosing it has to go through the CLI's picker so you actually see what you're opting into.

---

## Changing your workflow profile (the two-step process)

OpenSpec's profile change is intentionally a **two-step process**:

1. **`openspec config profile <preset>`** — updates the workflow set in your global CLI config
2. **`openspec update`** — installs the corresponding skills/command files for your AI tools

Step 1 alone is not enough — your AI tools won't see the new (or removed) commands until step 2 runs.

The plugin runs step 1 for you and then prompts for step 2. Three surfaces trigger the flow:

- **Status bar widget popup** — clicking the inactive preset (`○ custom` when you're on `core`, or `○ core` when you're on `custom`) switches via the shared profile-switch service.
- **Settings panel combo** — picking `core` or `(default)` and clicking Apply.
- **"Customize workflows…" button in Settings** — opens the CLI's interactive picker in IntelliJ's Terminal tool window. Once you finish picking, click **"I'm done"** in the inline banner; the plugin refreshes its profile cache and, if the workflow set actually changed, kicks off the same prompt.

After any of these, you see:

> *Profile changed to 'custom'. Run `openspec update` now to install skills for your AI tools (Cursor, Claude Code, GitHub Copilot, etc.)?*
>
> [Yes, update now] [Later]

- Choosing **Yes** runs `openspec update` in the project directory and reports success or failure.
- Choosing **Later** is a no-op — you can run `openspec update` manually whenever you're ready.

If the OpenSpec CLI is not detected, the plugin persists your profile choice locally and shows an informational notification — it does not run the update step (which requires the CLI). If IntelliJ's Terminal tool window isn't available (e.g. a stripped IDE distribution without the bundled terminal plugin), the "Customize workflows…" button falls back to copying `openspec config profile` to your clipboard and surfacing a notification with a docs link.

---

## How the plugin's profile-aware UI surfaces work

### Status bar widget

The bottom status bar shows your active workflow profile:

```
OpenSpec: core                          ← core profile (5 fixed workflows)
OpenSpec: custom · 8 workflows          ← custom profile (variable count)
OpenSpec: core (fallback)               ← CLI not detected
```

Hovering shows the full workflow list. Clicking opens a popup with:

- The active profile, marked `●`. For `custom` the label expands to `● custom (your workflow set) · N workflows  (active)` to make clear that `custom` is *what your set is called*, not a preset you toggled into from a fixed list.
- The other preset, marked `○` — one click to switch.
- A non-selectable discovery line: *"Run `openspec config profile` in a terminal to see what's available"*. The plugin does not enumerate workflow names here — the CLI's picker is the only authoritative source.
- An "Edit in Settings…" link.
- An "About profiles…" link to this page.

The widget is hidden in non-OpenSpec projects.

### Action text suffix `(custom)`

When an action is disabled because its workflow is not in your active profile, the action text gets a `(custom)` suffix in menus and toolbars:

```
Continue (custom)        ← disabled because you're on core
Verify (custom)          ← same
Fast-Forward (custom)    ← same
```

The suffix tells you *why* the action is greyed out without requiring a tooltip hover. It disappears the moment you switch to custom and `openspec update` runs.

### Settings panel — "Workflow profile" combo + "Customize workflows…" button

Open **Settings → Tools → OpenSpec**. The General section has a **Workflow profile** combo and a **"Customize workflows…"** button on the same row:

```
Workflow profile: [(default — uses CLI's active profile)  ▾ ]  [ Customize workflows… ]
                  [ core                                  ▾ ]
```

The combo lists only the two CLI-accepted presets: `(default)` and `core`. `custom` is intentionally absent — see the section above. To put yourself on a custom workflow set, click **"Customize workflows…"**; it launches `openspec config profile` in IntelliJ's Terminal tool window. When you're done picking, click **"I'm done"** in the inline banner that appears beneath the combo, and the plugin refreshes the active profile cache and prompts for `openspec update` if your set actually changed.

The `?` icon next to the label opens a tooltip explaining the core vs custom tradeoff and pointing at the same Customize button.

If your OpenSpec CLI isn't detected, the combo is disabled and an inline message tells you to install it.

If your existing settings hold an unknown profile name (e.g. `spec-driven` from a pre-1.x version), it appears as an orphan with a `(not found in CLI)` warning suffix in red, an inline recovery hint tells you to pick `(default)` or `core` or run the CLI picker, and **Apply is disabled** until you correct the value. The plugin no longer silently reverts orphans on apply — keeping a stale local value would mask the underlying CLI/setting drift.

### "Config Profile" details section

Below the combo, the Config Profile section shows the CLI's view of your active profile: name, description, and the full list of active workflows. This refreshes whenever you change profile in the combo.

---

## Quick reference

| Question | Answer |
|---|---|
| Where does the workflow profile live? | `~/.config/openspec/config.json` (global; per-user) |
| What are the presets? | `core` (5 fixed workflows), `custom` (any subset) |
| How do I switch via CLI? | `openspec config profile core` or `openspec config profile custom` |
| What's the second step? | `openspec update` — installs skills for your AI tools |
| Why is `Verify (custom)` greyed out? | You're on `core`; switch to `custom` to enable it |
| Can I customize which workflows are in `custom`? | Yes — click "Customize workflows…" in Settings (or run `openspec config profile` in a terminal) |
| Where is my project's `profile:` block? | `openspec/config.yaml` — that's project metadata, NOT the workflow profile |

---

## See also

- [[Menu-and-Actions-Reference]] — what each workflow action does
- [[Tool-Window-Guide]] — how the status bar widget fits in
- [OpenSpec CLI documentation](https://github.com/fission-ai/openspec) — upstream docs
