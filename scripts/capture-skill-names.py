#!/usr/bin/env python3
"""Capture each OpenSpec release's own list of skill names, as the oracle for the plugin's per-release list.

Upstream recognizes an OpenSpec skill only as `<skills folder>/<name>/SKILL.md` for a name in that release's
`SKILL_NAMES` (exported by `dist/core/shared/tool-detection.js` in every release; from 1.8.0 an alias of
`OPENSPEC_SKILL_NAMES` in `config.js`). For each release named, this writes

    src/test/resources/fixtures/cli/<version>/skill-names.json    {"version", "skillNames": [...],
                                                                   "adapterBackedWithLegacy": [...] (1.11.0 and later)}

`adapterBackedWithLegacy` lists the tools whose command surface is "adapter-backed" (`dist/core/command-surface.js`)
and that have older skills folders: from 1.11.0, `getConfiguredTools` counts such a tool's older-folder skills whoever
owns the shared folder.

The package comes from the shared scratch cache (scripts/lib), never `npx`.

Usage: scripts/capture-skill-names.py 1.3.0 1.5.0 1.6.0 ...
"""
import json
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "scripts" / "lib"))
from openspec_capture import package  # noqa: E402

FIXTURES = REPO / "src/test/resources/fixtures/cli"
DUMP = """
const m = await import(process.argv[1]);
process.stdout.write(JSON.stringify(m.SKILL_NAMES));
"""
ADAPTERS = """
const [s, c] = await Promise.all([import(process.argv[1]), import(process.argv[2])]);
process.stdout.write(JSON.stringify(c.AI_TOOLS.filter(t => s.resolveCommandSurfaceCapability(t.value) === 'adapter-backed'
  && (t.legacySkillsDirs || []).length).map(t => t.value)));
"""


def main():
    versions = sys.argv[1:]
    if not versions:
        sys.exit("name the releases")
    for v in versions:
        js = (package(v) / "dist/core/shared/tool-detection.js").as_uri()
        names = json.loads(subprocess.run(["node", "--input-type=module", "-e", DUMP, "--", js],
                                          check=True, capture_output=True, text=True).stdout)
        data = {"version": v, "skillNames": names}
        surface = package(v) / "dist/core/command-surface.js"
        if surface.is_file():
            data["adapterBackedWithLegacy"] = json.loads(subprocess.run(
                ["node", "--input-type=module", "-e", ADAPTERS, "--", surface.as_uri(),
                 (package(v) / "dist/core/config.js").as_uri()], check=True, capture_output=True, text=True).stdout)
        out = FIXTURES / v / "skill-names.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(data, indent=2) + "\n")
        print(f"  {v}: {len(names)} names")


if __name__ == "__main__":
    main()
