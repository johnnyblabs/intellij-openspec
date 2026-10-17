# JetBrains Marketplace Page Content

Reference for filling in the [plugin edit page](https://plugins.jetbrains.com/plugin/30678-openspec/edit).

> **Maintenance: Reference** — stable; updated only when the Marketplace listing copy changes (see the [documentation index](README.md)).

---

## Short Description (search results line)

For `build.gradle.kts` `description` field (max ~80 chars, shown in marketplace search):

```
IDE-native client for the OpenSpec spec-driven development framework by Fission AI
```

---

## Description (main marketplace page)

Generated, not pasted: the release build takes the text between the `<!-- Plugin description -->` markers in [`README.md`](../README.md) and writes it into `plugin.xml`, and every Marketplace upload replaces the listing's Description with it. Edit README.md to change it.

---

## Getting Started, other fields, and tags

These fields are edited by hand in the Marketplace dashboard; only the Description above updates on release. Their exact values live in [`docs/marketplace/`](marketplace/):

- [`getting-started.html`](marketplace/getting-started.html) — paste into the **Getting Started** field.
- [`listing.json`](marketplace/listing.json) — the URL fields, copyright, tags, the expected screenshot count, and **Preserve changes from UI** (must stay off, or releases stop updating the Description from README.md).

`scripts/check-marketplace-listing.py` compares those files with the public listing and prints each field that differs. It runs weekly in CI, which opens a tracker issue on drift, and as a release-prep step. When a listing change is intended, update the files here first, then paste.

---

## Media / Screenshots

The tour shots live in [`docs/screenshots/`](screenshots/) — regenerate them with `./gradlew screenshotTour`. The listing currently has 13 (the count is checked against `listing.json`). Lead with the viewing/intelligence visuals:

1. **Spec + change overview** (`01-spec-browser`) — a spec open in the editor with Markdown preview, beside the Changes-focused Browse tree and its consolidated change-deltas
2. **Change deltas** (`08-change-deltas`) — the consolidated deltas view, grouped by capability/operation with badges
3. **Tree badges** (`09-tree-badges`) — a change expanded showing artifact-status badge overlays (done / ready / blocked, X/Y progress)
4. **Validation console** (`10-validation-console`) — grouped-by-file results with file:line links and per-severity coloring
5. **Workflow / pipeline** (`02-change-workflow`) — pipeline chips showing progress on an active change

**Tips:**
- Use the default IntelliJ light theme (most recognizable on the marketplace)
- Crop to the relevant panel area, not the full IDE window
- Use a sample project that looks realistic
- Recommended size: 1280x800 or similar 16:10 ratio

---

## Contacts & Resources

| Field | Value |
|-------|-------|
| **Vendor URL** | `https://openspec.johnnyblabs.com` |
| **Email** | Personal email for now (update to `openspec@johnnyblabs.com` once email forwarding is set up) |

---

## Monetization

Free — no monetization settings needed.
