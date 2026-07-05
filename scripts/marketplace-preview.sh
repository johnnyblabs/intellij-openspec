#!/usr/bin/env bash
#
# marketplace-preview.sh — render, locally, everything the next release will put
# on the JetBrains Marketplace listing, plus a checklist of the surfaces that
# only change via the Marketplace web UI (screenshots, tags, listing links).
#
# The description and change notes are extracted from the PATCHED plugin.xml —
# the exact artifact content the Marketplace reads — not re-derived, so what
# you preview is what ships.
#
# Usage:  scripts/marketplace-preview.sh
# Output: build/marketplace-preview.html  (path printed; open in a browser)
#
# "What's New" logic: if CHANGELOG.md has a non-empty "## Unreleased" section,
# that is what the next release will carry (post-release-cut it becomes the
# version section), so it is previewed and labeled as such. Otherwise the
# current project version's section is shown.

set -euo pipefail
cd "$(dirname "$0")/.."

echo "▶ Rendering the patched plugin.xml (description + change notes as they ship)…"
./gradlew -q patchPluginXml

PATCHED="build/tmp/patchPluginXml/plugin.xml"
[ -f "$PATCHED" ] || { echo "✖ $PATCHED not found"; exit 1; }

OUT="build/marketplace-preview.html"

python3 - "$PATCHED" "$OUT" <<'PYEOF'
import html
import re
import subprocess
import sys
from pathlib import Path

patched = Path(sys.argv[1]).read_text(encoding="utf-8")
out = Path(sys.argv[2])

def cdata(tag: str) -> str:
    m = re.search(rf"<{tag}>\s*<!\[CDATA\[(.*?)\]\]>\s*</{tag}>", patched, re.S)
    return m.group(1).strip() if m else f"(no <{tag}> in patched plugin.xml)"

def attr(pattern: str) -> str:
    m = re.search(pattern, patched)
    return m.group(1) if m else "?"

description = cdata("description")
change_notes = cdata("change-notes")
version = attr(r"<version>([^<]+)</version>")
since = attr(r'since-build="([^"]+)"')
name = attr(r"<name>([^<]+)</name>")
vendor = attr(r'<vendor[^>]*>([^<]+)</vendor>')
vendor_url = attr(r'<vendor url="([^"]+)"')
since_human = f"20{since[:2]}.{since[2:]}" if re.fullmatch(r"\d{3}", since) else since

# Unreleased section takes precedence for What's New: that is the next release.
unreleased = subprocess.run(
    ["./gradlew", "-q", "getChangelog", "--no-header", "--unreleased"],
    capture_output=True, text=True)
unreleased_md = unreleased.stdout.strip() if unreleased.returncode == 0 else ""
if unreleased_md:
    notes_label = f"Unreleased (ships as the next release; current version is {version})"
    # Render the unreleased markdown the same way changeNotes does: cheap fallback
    # is to show the version-section HTML only when no unreleased content exists,
    # so here we present the markdown pre-wrapped (structure is what matters).
    notes_html = "<pre style='white-space:pre-wrap'>" + html.escape(unreleased_md) + "</pre>"
else:
    notes_label = f"v{version} (from the patched artifact)"
    notes_html = change_notes

icon = Path("src/main/resources/META-INF/pluginIcon.svg")
icon_svg = icon.read_text(encoding="utf-8") if icon.exists() else ""

# Leak scrub of exactly what this page previews (same pattern set as the
# release workflow's pre-publish gate).
leak = re.compile(
    r"forgejo|(^|[^a-zA-Z])geek([^a-zA-Z]|$)|(^|[^a-zA-Z])plane([^a-zA-Z]|$)"
    r"|OSPEC-[0-9]|OSP-[0-9]|johnb/", re.I | re.M)
scrub_hits = [s for s in (description, change_notes, unreleased_md) if s and leak.search(s)]
scrub_line = ("✅ clean — no internal identifiers in the previewed content"
              if not scrub_hits else
              "❌ INTERNAL IDENTIFIER FOUND in previewed content — fix before releasing")

manual = """
<ul>
  <li><b>Screenshots / media</b> — web UI only (no API). Re-shoot and re-upload when the
      tool window / settings UI changed since the last release. Check the archived changes
      for this release for UI-touching work.</li>
  <li><b>Tags</b> — set in the listing profile (currently: AI, Code Tools, Productivity,
      Tools Integration). Persist across releases; verify they still fit.</li>
  <li><b>Listing links</b> — source code, documentation, issue tracker, license URL live in
      the Marketplace profile, not the artifact. Verify they resolve.</li>
  <li><b>Everything above this list needs NO upload</b> — description, change notes, icon,
      version, vendor, and compatibility all ride inside the signed zip.</li>
</ul>
"""

page = f"""<!doctype html><html><head><meta charset="utf-8">
<title>Marketplace preview — {html.escape(name)} {html.escape(version)}</title>
<style>
 body {{ font-family: -apple-system, 'Segoe UI', sans-serif; max-width: 880px;
        margin: 2rem auto; padding: 0 1rem; color: #1f2326; line-height: 1.55; }}
 header {{ display: flex; gap: 1rem; align-items: center; }}
 header .icon svg {{ width: 64px; height: 64px; }}
 .meta {{ color: #5c6a72; }}
 section {{ border: 1px solid #d5dbe0; border-radius: 8px; padding: 1rem 1.4rem;
           margin: 1.2rem 0; }}
 section > h2 {{ margin-top: 0; font-size: 1rem; text-transform: uppercase;
                letter-spacing: .05em; color: #5c6a72; }}
 .scrub {{ font-weight: 600; }}
</style></head><body>
<header>
  <div class="icon">{icon_svg}</div>
  <div>
    <h1 style="margin:0">{html.escape(name)}</h1>
    <div class="meta">v{html.escape(version)} · {html.escape(vendor)} ·
      <a href="{html.escape(vendor_url)}">{html.escape(vendor_url)}</a> ·
      IntelliJ-family IDEs {html.escape(since_human)}+</div>
  </div>
</header>
<section><h2>Description (ships in the artifact — from README's marker section)</h2>
{description}</section>
<section><h2>What's New — {html.escape(notes_label)}</h2>
{notes_html}</section>
<section><h2>Scrub check</h2><div class="scrub">{scrub_line}</div></section>
<section><h2>Manual surfaces — web UI only, review before/after release</h2>
{manual}</section>
</body></html>"""

out.write_text(page, encoding="utf-8")
print(f"✓ wrote {out}")
PYEOF

echo ""
echo "Open it:  open $OUT"
