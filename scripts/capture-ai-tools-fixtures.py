#!/usr/bin/env python3
"""Capture the OpenSpec CLI's AI-tool registry and tool detection as contract fixtures.

No CLI command lists the tools, so the registry is read from the package itself: `AI_TOOLS` and
`TOOL_ID_ALIASES` exported by `dist/core/config.js`. For each published version from 1.3.0 up, the
package is installed into a scratch prefix and dumped. A dump is written only when the registry
differs from the previous version's, and always for the newest version (the plugin's target), so
each version that has no fixture has the same registry as the nearest earlier one that does:

    src/test/resources/fixtures/cli/<version>/ai-tools.json    {"version", "tools", "aliases"}

For the newest version it also records `openspec init --help` and a set of detection cases:

    src/test/resources/fixtures/cli/<version>/init-help.txt
    src/test/resources/fixtures/cli/<version>/detect/<case>/layout.json    {"project": {path: content}, "home": {...}}
    src/test/resources/fixtures/cli/<version>/detect/<case>/detected.json  tool ids upstream detects

Each case is a project built by real `openspec init --tools ...` runs (some with an older CLI, for
the directories tools used before) or by hand for edge cases, then passed to upstream's own
`getAvailableTools` (`dist/core/available-tools.js`). Layouts keep every file's path. Content is
kept for ownership markers (`.openspec-target`) and, for generated `SKILL.md` files, only the lines
naming a skill (`$openspec-...` or `/openspec-...`), which is what upstream reads to infer an
unmarked shared root's owner; other files are recorded empty.

Every run uses an isolated HOME and XDG directories, so the real global OpenSpec state is untouched.

Usage: scripts/capture-ai-tools-fixtures.py                 (every version from 1.3.0)
       CAPTURE_VERSIONS=1.14.3 scripts/capture-ai-tools-fixtures.py
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
FIXTURES = REPO / "src/test/resources/fixtures/cli"
PACKAGE = "@fission-ai/openspec"
FLOOR = (1, 3, 0)

DUMP = """
import(process.argv[1]).then(m => process.stdout.write(JSON.stringify({
  tools: m.AI_TOOLS, aliases: m.TOOL_ID_ALIASES || {}
}, null, 2)));
"""
DETECT = """
import(process.argv[1]).then(m => process.stdout.write(JSON.stringify(
  m.getAvailableTools(process.argv[2]).map(t => t.value))));
"""

# Detection cases: name -> list of steps. ("init", version-or-None, tools) runs a real init with that
# CLI (None = the newest); ("file", path, content) writes a file; ("rm", path) removes one; ("home-init",
# tools) runs init so tools with a global skills directory write into the isolated HOME.
CASES = {
    "claude": [("init", None, "claude")],
    "devin-via-windsurf-id": [("init", None, "windsurf")],
    "windsurf-dir-from-1.6": [("init", "1.6.0", "windsurf")],
    "codex": [("init", None, "codex")],
    "codex-dir-from-1.6": [("init", "1.6.0", "codex")],
    "antigravity": [("init", None, "antigravity")],
    "antigravity-dir-from-1.6": [("init", "1.6.0", "antigravity")],
    "codex-and-antigravity": [("init", None, "codex,antigravity")],
    "kimi": [("init", None, "kimi")],
    "kimi-dir-from-1.6": [("init", "1.6.0", "kimi")],
    "github-copilot": [("init", None, "github-copilot")],
    "bare-github": [("file", ".github/workflows/ci.yml", "")],
    "copilot-instructions-only": [("file", ".github/copilot-instructions.md", "")],
    "agents-universal": [("init", None, "agents")],
    "agents-unmarked-generic": [("init", None, "agents"), ("rm", ".agents/skills/.openspec-target")],
    "agents-unmarked-codex-syntax": [("init", None, "codex"), ("rm", ".agents/skills/.openspec-target")],
    "agents-empty-dir": [("file", ".agents/.keep", "")],
    "codex-legacy-beside-unmarked-agents": [("init", "1.6.0", "codex"), ("init", None, "agents"),
                                            ("rm", ".agents/skills/.openspec-target")],
    "codex-legacy-beside-empty-agents-skills": [("init", "1.6.0", "codex"), ("file", ".agents/skills/.keep", "")],
    "amp": [("init", None, "amp")],
    "zed": [("init", None, "zed")],
    "gsd": [("init", None, "gsd")],
    "gsd-dir-only": [("file", ".gsd/.keep", "")],
    "hermes-file": [("file", "HERMES.md", "")],
    "warp-file": [("file", "WARP.md", "")],
    "rovodev": [("init", None, "rovodev")],
    "roocode": [("init", None, "roocode")],
    "bob": [("init", None, "bob")],
    "minimax-global": [("home-init", "minimax-code")],
    "several": [("init", None, "claude,cursor,github-copilot,gemini")],
}


def key(v):
    return tuple(int(p) for p in v.split("."))


def published_versions():
    out = subprocess.run(["npm", "view", PACKAGE, "versions", "--json"], check=True,
                         capture_output=True, text=True).stdout
    return sorted((v for v in json.loads(out) if "-" not in v and key(v) >= FLOOR), key=key)


def install(version, scratch):
    prefix = scratch / "pkg" / version
    if not prefix.exists():
        subprocess.run(["npm", "install", "--silent", "--no-audit", "--no-fund", "--prefix", str(prefix),
                        f"{PACKAGE}@{version}"], check=True, capture_output=True)
    return prefix / "node_modules" / PACKAGE


def node(script, *args):
    return subprocess.run(["node", "--input-type=module", "-e", script, "--", *args],
                          check=True, capture_output=True, text=True).stdout


def dump(pkg):
    return json.loads(node(DUMP, (pkg / "dist/core/config.js").as_uri()))


def isolated_env(home):
    env = dict(os.environ, HOME=str(home), OPENSPEC_TELEMETRY="0", DO_NOT_TRACK="1", NO_COLOR="1",
               OPEN_SPEC_INTERACTIVE="0")
    for var, sub in [("XDG_CONFIG_HOME", ".config"), ("XDG_DATA_HOME", ".local/share"),
                     ("XDG_STATE_HOME", ".local/state"), ("XDG_CACHE_HOME", ".cache")]:
        env[var] = str(home / sub)
    return env


def init(pkg, project, home, tools):
    subprocess.run(["node", str(pkg / "bin/openspec.js"), "init", "--tools", tools], cwd=project,
                   env=isolated_env(home), check=True, capture_output=True, text=True)


def recorded(path):
    if path.name == ".openspec-target":
        return path.read_text()
    if path.name == "SKILL.md":
        lines = path.read_text().splitlines()
        return "".join(l + "\n" for l in lines if "$openspec-" in l or "/openspec-" in l)
    return ""


def snapshot(root, skip=()):
    if not root.exists():
        return {}
    files = {}
    for p in sorted(root.rglob("*")):
        rel = p.relative_to(root).as_posix()
        if p.is_file() and not any(rel == s or rel.startswith(s + "/") for s in skip):
            files[rel] = recorded(p)
    return files


def capture_detection(newest, scratch, out_dir):
    pkg = install(newest, scratch)
    detect_js = (pkg / "dist/core/available-tools.js").as_uri()
    for name, steps in CASES.items():
        case = scratch / "cases" / name
        project, home = case / "project", case / "home"
        project.mkdir(parents=True)
        home.mkdir()
        for step in steps:
            if step[0] == "init":
                init(install(step[1] or newest, scratch), project, home, step[2])
            elif step[0] == "home-init":
                init(pkg, project, home, step[1])
            elif step[0] == "file":
                f = project / step[1]
                f.parent.mkdir(parents=True, exist_ok=True)
                f.write_text(step[2])
            elif step[0] == "rm":
                (project / step[1]).unlink()
        detected = json.loads(subprocess.run(
            ["node", "--input-type=module", "-e", DETECT, "--", detect_js, str(project)],
            env=isolated_env(home), check=True, capture_output=True, text=True).stdout)
        # The openspec/ tree and the CLI's own XDG state say nothing about tools.
        layout = {"project": snapshot(project, skip=("openspec",)),
                  "home": snapshot(home, skip=(".config", ".local", ".cache", ".npm"))}
        target = out_dir / "detect" / name
        target.mkdir(parents=True, exist_ok=True)
        (target / "layout.json").write_text(json.dumps(layout, indent=2) + "\n")
        (target / "detected.json").write_text(json.dumps(sorted(detected)) + "\n")
        print(f"  detect/{name}: {sorted(detected)}")


def main():
    wanted = os.environ.get("CAPTURE_VERSIONS")
    versions = published_versions()
    if wanted:
        versions = [v for v in versions if v in wanted.split(",")]
    with tempfile.TemporaryDirectory() as tmp:
        scratch = Path(tmp)
        previous = None
        for version in versions:
            registry = dump(install(version, scratch))
            if registry != previous or version == versions[-1]:
                out_dir = FIXTURES / version
                out_dir.mkdir(parents=True, exist_ok=True)
                (out_dir / "ai-tools.json").write_text(
                    json.dumps({"version": version, **registry}, indent=2) + "\n")
                print(f"{version}: {len(registry['tools'])} tools")
            else:
                print(f"{version}: unchanged")
            previous = registry
        newest = versions[-1]
        out_dir = FIXTURES / newest
        home = scratch / "help-home"
        home.mkdir()
        help_text = subprocess.run(["node", str(install(newest, scratch) / "bin/openspec.js"), "init", "--help"],
                                   env=isolated_env(home), check=True, capture_output=True, text=True).stdout
        (out_dir / "init-help.txt").write_text(help_text)
        if (out_dir / "detect").exists():
            shutil.rmtree(out_dir / "detect")
        capture_detection(newest, scratch, out_dir)


if __name__ == "__main__":
    sys.exit(main())
