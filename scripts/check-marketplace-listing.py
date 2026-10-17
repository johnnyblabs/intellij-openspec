#!/usr/bin/env python3
"""Check the JetBrains Marketplace listing against the repo's copy of its hand-edited fields.

The plugin Description is generated from README.md on every release, so it cannot drift. The
other listing fields (Getting Started, URLs, tags, copyright, screenshots) can only be edited in
the Marketplace web dashboard. Their expected values live in docs/marketplace/; this script reads
the public listing API (no token needed) and reports every field that differs, with what to paste.

Exit codes: 0 in sync, 1 drift found, 2 the listing could not be read.

Usage:
    scripts/check-marketplace-listing.py [--live-json FILE]

--live-json compares a saved API response instead of fetching the live one.
"""
import argparse
import difflib
import html
import json
import re
import sys
import urllib.request
from html.parser import HTMLParser
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
EXPECTED = REPO / "docs/marketplace/listing.json"
GETTING_STARTED = REPO / "docs/marketplace/getting-started.html"
API = "https://plugins.jetbrains.com/api/plugins/{id}"

BLOCK_TAGS = {"p", "li", "h1", "h2", "h3", "h4", "pre", "ol", "ul", "br", "div", "tr"}


class _TextOf(HTMLParser):
    """Renders HTML as comparable text: one line per block, links kept as 'text <href>'."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.out = []
        self.hrefs = []

    def handle_starttag(self, tag, attrs):
        if tag in BLOCK_TAGS:
            self.out.append("\n")
        if tag == "a":
            self.hrefs.append(dict(attrs).get("href", ""))

    def handle_endtag(self, tag):
        if tag == "a" and self.hrefs:
            self.out.append(f" <{self.hrefs.pop()}>")
        if tag in BLOCK_TAGS:
            self.out.append("\n")

    def handle_data(self, data):
        self.out.append(data)


def text_lines(markup):
    parser = _TextOf()
    parser.feed(markup or "")
    parser.close()
    text = html.unescape("".join(parser.out))
    return [re.sub(r"\s+", " ", line).strip() for line in text.split("\n") if line.strip()]


def fetch(plugin_id):
    req = urllib.request.Request(API.format(id=plugin_id), headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def compare(expected, live, getting_started_html):
    """Returns a list of (field, detail) drift findings."""
    drift = []

    want = text_lines(getting_started_html)
    have = text_lines(live.get("docText"))
    if want != have:
        diff = "\n".join(difflib.unified_diff(have, want, "live", "repo", lineterm=""))
        drift.append(("Getting Started", "Paste docs/marketplace/getting-started.html into the "
                      "Getting Started field.\n" + diff))

    for key, value in expected["urls"].items():
        live_value = (live.get("urls") or {}).get(key, "")
        if (live_value or "").strip() != value:
            drift.append((f"URL {key}", f"live {live_value!r}, expected {value!r}"))

    if (live.get("copyright") or "").strip() != expected["copyright"]:
        drift.append(("Copyright", f"live {live.get('copyright')!r}, expected {expected['copyright']!r}"))

    live_tags = sorted(t.get("name", "") for t in live.get("tags") or [])
    if live_tags != expected["tags"]:
        drift.append(("Tags", f"live {live_tags}, expected {expected['tags']}"))

    if bool(live.get("preserveUIEdits")) != expected["preserveUIEdits"]:
        drift.append(("Preserve changes from UI", f"live {live.get('preserveUIEdits')}, expected "
                      f"{expected['preserveUIEdits']}: when on, releases stop updating the Description "
                      "from README.md"))

    screens = len(live.get("screens") or [])
    if screens != expected["screenshots"]:
        drift.append(("Screenshots", f"live has {screens}, docs/marketplace/listing.json expects "
                      f"{expected['screenshots']}. Update the count (and the media list in "
                      "docs/marketplace-page.md) if the change was intended."))
    return drift


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--live-json", type=Path, help="compare a saved API response instead of fetching")
    args = ap.parse_args()

    expected = json.loads(EXPECTED.read_text())
    try:
        live = json.loads(args.live_json.read_text()) if args.live_json else fetch(expected["pluginId"])
    except Exception as e:  # network, HTTP or JSON failure: not drift, but not a pass either
        print(f"Could not read the Marketplace listing: {e}", file=sys.stderr)
        return 2

    drift = compare(expected, live, GETTING_STARTED.read_text())
    if not drift:
        print("Marketplace listing matches docs/marketplace/.")
        return 0
    print(f"Marketplace listing drift: {len(drift)} field(s) differ from docs/marketplace/.\n")
    for field, detail in drift:
        print(f"## {field}\n{detail}\n")
    print("Fix the listing at the plugin's Marketplace edit page, or update docs/marketplace/ if the "
          "live value is the intended one.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
