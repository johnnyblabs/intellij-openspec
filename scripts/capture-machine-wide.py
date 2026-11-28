#!/usr/bin/env python3
"""Capture what the OpenSpec CLI (2.1+) does with skills installed machine-wide, as the oracle for the plugin.

From 2.1.0 a plain `openspec init` installs a tool's skills for every project on the machine (`~/.agents/skills`, linked
from each tool's own home folder). Upstream keeps that apart from a project being set up for a tool:
`getConfiguredTools` reads only the project, while `readGlobalSkills` / `projectHasOwnSkills`
(`dist/core/global-skills.js`) describe the machine. For each release and case this writes, under
`src/test/resources/fixtures/cli/<version>/machine-wide/<case>/`:

    layout.json          the project and home trees before the probe ({"project": {...}, "home": {...}}, "links")
    probe.json           {"global": readGlobalSkills(), "own": projectHasOwnSkills(project),
                          "configured": getConfiguredTools(project), "available": getAvailableTools(project)}
    init.out / .exit     `openspec init --tools <tool>` in the project, default scope (stdout, then stderr)
    after.json           the probe again, after that init
    update.out / .exit   `openspec update` in the project afterwards
    global-support.json  (per release) the tools upstream's `supportsGlobalSkills` rejects, and each other tool's
                         machine folder relative to HOME (`.agents/skills` for universal agents)

Every run uses an isolated HOME and XDG directories, `OPENSPEC_TELEMETRY=0`, and a package from the scratch cache
(scripts/lib), never `npx`. Paths are replaced with /fixture/demo-project and /fixture/home.

Usage: scripts/capture-machine-wide.py 2.1.0 2.1.1 2.2.0
"""
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "scripts" / "lib"))
from openspec_capture import cli, isolated_env, package, run, sanitize, snapshot, write_fixture  # noqa: E402

FIXTURES = REPO / "src/test/resources/fixtures/cli"
PROBE = """
const [g, t, a] = await Promise.all([import(process.argv[1]), import(process.argv[2]), import(process.argv[3])]);
const project = process.argv[4];
process.stdout.write(JSON.stringify({global: await g.readGlobalSkills(), own: g.projectHasOwnSkills(project),
  configured: t.getConfiguredTools(project).map(x => typeof x === 'string' ? x : x.value).sort(),
  available: a.getAvailableTools(project).map(x => x.value).sort()}));
"""
SUPPORT = """
import os from 'node:os';
import path from 'node:path';
import { getSupportedAgents } from 'skills';
const [g, c] = await Promise.all([import(process.argv[1]), import(process.argv[2])]);
// Upstream's globalDirectories(): the shared folder for universal agents, a dedicated one for the rest (and for the two
// Antigravity agents); each tool's machine folder is its installer agent's, else the shared one.
const shared = path.join(os.homedir(), '.agents', 'skills');
const dirs = new Map([['universal', shared]]);
for (const a of getSupportedAgents()) {
  if ((!a.universal || a.id === 'antigravity' || a.id === 'antigravity-cli') && a.globalSkillsDir) dirs.set(a.id, a.globalSkillsDir);
}
const rel = d => path.relative(os.homedir(), d).split(path.sep).join('/');
const folders = Object.fromEntries(c.AI_TOOLS.filter(t => g.supportsGlobalSkills(t.value))
  .map(t => [t.value, rel(dirs.get(c.installerAgent(t.value).id) ?? shared)]));
process.stdout.write(JSON.stringify({noGlobalSupport: c.AI_TOOLS.filter(t => t.skillsDir && !g.supportsGlobalSkills(t.value)).map(t => t.value),
  machineFolders: folders}));
"""
SKILL = "Run /openspec-explore.\n"

# name -> (steps, tool passed to `init --tools` in the project, project-is-home)
# steps: ("global", tools) runs `init --tools <tools> --scope global` in a scratch folder; ("project-init", tools) runs
# `init --tools <tools> --scope project` in the project; ("file", path) and ("home-file", path) write a skill or a
# marker; ("home-rmtree", path) removes a home folder.
CASES = {
    "agents-global": ([("global", "claude"), ("file", ".claude/.keep")], "claude", False),
    "home-copies-only": ([("file", ".claude/.keep"), ("home-file", ".claude/skills/openspec-explore/SKILL.md")],
                         "claude", False),
    "links-only": ([("global", "claude"), ("home-rmtree", ".agents/skills"), ("file", ".claude/.keep")], "claude", False),
    "project-is-home": ([("global", "claude"), ("home-file", ".claude/.keep")], "claude", True),
    "uncovered-tool": ([("global", "claude"), ("file", ".eve/.keep")], "eve", False),
    "project-and-global": ([("global", "claude"), ("project-init", "claude")], "claude", False),
    # A project whose only set-up tool is a command file has skills of its own to upstream: init goes to the project.
    "project-commands-only": ([("global", "claude"), ("file", ".claude/commands/opsx/propose.md"), ("file", ".cursor/.keep")],
                              "cursor", False),
    # A saved scope (OPENSPEC_SKILLS_SCOPE) of project sends init to the project.
    "saved-scope-project": ([("global", "claude"), ("file", ".claude/.keep"), ("env", "OPENSPEC_SKILLS_SCOPE", "project")],
                            "claude", False),
}


def modules(pkg):
    d = pkg / "dist/core"
    return [(d / "global-skills.js").as_uri(), (d / "shared/tool-detection.js").as_uri(),
            (d / "available-tools.js").as_uri()]


def probe(pkg, project, home, **env):
    return json.loads(subprocess.run(["node", "--input-type=module", "-e", PROBE, "--", *modules(pkg), str(project)],
                                     env=isolated_env(home, **env), check=True, capture_output=True, text=True).stdout)


def capture(version):
    pkg = package(version)
    # From the package's folder, so its own dependencies (the `skills` agent list) resolve; HOME is the fixture's.
    scratch_home = Path(tempfile.mkdtemp(prefix="osp-machine-home-"))
    support = json.loads(subprocess.run(["node", "--input-type=module", "-e", SUPPORT, "--",
                                         (pkg / "dist/core/global-skills.js").as_uri(),
                                         (pkg / "dist/core/config.js").as_uri()],
                                        cwd=pkg, env=isolated_env(scratch_home), check=True, capture_output=True,
                                        text=True).stdout)
    shutil.rmtree(scratch_home, ignore_errors=True)
    write_fixture(FIXTURES / version / "machine-wide" / "global-support.json",
                  json.dumps({"version": version, "noGlobalSupport": sorted(support["noGlobalSupport"]),
                              "machineFolders": dict(sorted(support["machineFolders"].items()))}, indent=2) + "\n")
    for name, (steps, tool, at_home) in CASES.items():
        scratch = Path(tempfile.mkdtemp(prefix="osp-machine-"))
        try:
            home = scratch / "home"
            project = home if at_home else scratch / "project"
            other = scratch / "other"
            for d in (home, project, other):
                d.mkdir(parents=True, exist_ok=True)
            env = {}
            for step in steps:
                if step[0] == "env":
                    env[step[1]] = step[2]
                elif step[0] == "global":
                    run(cli(version) + ["init", "--tools", step[1], "--scope", "global"], other, home)
                elif step[0] == "project-init":
                    run(cli(version) + ["init", "--tools", step[1], "--scope", "project"], project, home)
                elif step[0] in ("file", "home-file"):
                    f = (project if step[0] == "file" else home) / step[1]
                    f.parent.mkdir(parents=True, exist_ok=True)
                    f.write_text(SKILL if f.name == "SKILL.md" else "")
                elif step[0] == "home-rmtree":
                    shutil.rmtree(home / step[1])
            out = FIXTURES / version / "machine-wide" / name
            skip = (".config", ".local", ".cache", ".npm")
            files, links = snapshot(project, skip=("openspec",) + (skip if at_home else ()))
            hfiles, hlinks = ({}, {}) if at_home else snapshot(home, skip=skip)
            layout = {"project": files, "home": hfiles}
            if links or hlinks:
                layout["links"] = {k: v for k, v in (("project", links), ("home", hlinks)) if v}
            clean = lambda s: sanitize(s, project if not at_home else None, home)  # noqa: E731
            write_fixture(out / "layout.json", clean(json.dumps(layout, indent=2) + "\n"))
            write_fixture(out / "probe.json", clean(json.dumps(probe(pkg, project, home, **env), indent=2) + "\n"))
            for cmd, args in (("init", ["init", "--tools", tool]), ("update", ["update"])):
                r = run(cli(version) + args, project, home, check=False, **env)
                write_fixture(out / f"{cmd}.out", clean(r.stdout + ("\n--- stderr ---\n" + r.stderr if r.stderr else "")))
                write_fixture(out / f"{cmd}.exit", f"{r.returncode}\n")
                if cmd == "init":
                    write_fixture(out / "after.json", clean(json.dumps(probe(pkg, project, home, **env), indent=2) + "\n"))
            print(f"  {version} {name}: configured {probe(pkg, project, home, **env)['configured']}")
        finally:
            shutil.rmtree(scratch, ignore_errors=True)


def main():
    if len(sys.argv) < 2:
        sys.exit("name the releases, e.g. 2.1.0 2.1.1 2.2.0")
    for v in sys.argv[1:]:
        capture(v)


if __name__ == "__main__":
    main()
