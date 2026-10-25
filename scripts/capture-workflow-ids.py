#!/usr/bin/env python3
"""Capture upstream OpenSpec's workflow ids as a contract fixture for WorkflowParityContractTest.

The CLI has no command that prints its workflow set (`openspec config set workflows` accepts any value, and
`config profile` is interactive), so this loads the installed package's own `dist/core/profiles.js` with node and
records its exported CORE_WORKFLOWS and ALL_WORKFLOWS, in upstream's order, to
src/test/resources/fixtures/cli/<version>/workflows.json:

    {"cliVersion": "1.15.0", "core": [...], "all": [...]}

It fails rather than guessing if the package layout or the exports change.

Usage: scripts/capture-workflow-ids.py   (uses the `openspec` on PATH, or $OPENSPEC_BIN pointing at its executable)
"""
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent


def package_dir() -> Path:
    exe = os.environ.get("OPENSPEC_BIN") or shutil.which("openspec")
    if not exe:
        sys.exit("openspec not found on PATH; set OPENSPEC_BIN")
    # The npm install links bin/openspec -> <package>/bin/openspec.js.
    real = Path(exe).resolve()
    for parent in real.parents:
        if (parent / "package.json").is_file() and (parent / "dist/core/profiles.js").is_file():
            return parent
    sys.exit(f"no OpenSpec package with dist/core/profiles.js above {real} (the standalone binary has no JS to load)")


def main():
    pkg = package_dir()
    version = json.loads((pkg / "package.json").read_text())["version"]
    script = ("import(process.argv[1]).then(m => { "
              "if (!Array.isArray(m.CORE_WORKFLOWS) || !Array.isArray(m.ALL_WORKFLOWS)) "
              "{ console.error('profiles.js no longer exports CORE_WORKFLOWS / ALL_WORKFLOWS'); process.exit(2); } "
              "console.log(JSON.stringify({core: m.CORE_WORKFLOWS, all: m.ALL_WORKFLOWS})); })")
    result = subprocess.run(["node", "-e", script, (pkg / "dist/core/profiles.js").as_uri()],
                            capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"node failed: {result.stderr.strip()}")
    data = json.loads(result.stdout)
    fixture = {"cliVersion": version, "core": data["core"], "all": data["all"]}
    out = REPO / f"src/test/resources/fixtures/cli/{version}/workflows.json"
    out.write_text(json.dumps(fixture, indent=2) + "\n")
    print(f"wrote {out}: {len(fixture['core'])} core, {len(fixture['all'])} total")


if __name__ == "__main__":
    main()
