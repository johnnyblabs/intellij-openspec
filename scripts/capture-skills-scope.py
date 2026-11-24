#!/usr/bin/env python3
"""Capture how CLI 2.1's init decides the skills scope, and what the plugin reads back.

Writes, for each release in RELEASES (2.1.0 and 2.1.1, plus 2.0.0 for the environment check):

    fixtures/cli/<v>/skills-scope/remembered-config.yaml    the config upstream's own rememberProjectSkills writes
    fixtures/cli/<v>/skills-scope/config-team.yaml          the input config with an invalid skillsScope
    fixtures/cli/<v>/skills-scope/team-warning.{stderr,exit} what `instructions` prints for it
    fixtures/cli/<v>/skills-scope/team-context.txt          the config's context, still applied despite the warning
    fixtures/cli/<v>/skills-scope/<case>/layout.json        where an init put the skills (project and home)
    fixtures/cli/<v>/errors/<case>.{stdout,stderr,exit}     the init errors the plugin can meet
    fixtures/cli/<v>/skills-scope/machine-markers.json      (2.1.x) the paths the installer checks to call a tool
                                                            installed on this machine, each confirmed alone
    src/main/resources/openspec/machine-tool-markers.txt    the newest release's tickable markers, which the plugin
                                                            reads to tick tools in "Install into this project"

2.0.0 gets only env-ignored/: an init with OPENSPEC_SKILLS_SCOPE=project, which must match one without it.
Every run uses a throwaway HOME (scripts/lib/openspec_capture.py); 2.1.0 needs git and the network.

    python3 scripts/capture-skills-scope.py            # all releases
    CAPTURE_VERSIONS=2.1.1 python3 scripts/capture-skills-scope.py
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from openspec_capture import cli, isolated_env, key, package, require_git_and_network, sanitize, snapshot, \
    write_fixture  # noqa: E402

FIXTURES = Path(__file__).resolve().parent.parent / "src/test/resources/fixtures/cli"
RELEASES = ["2.0.0", "2.1.0", "2.1.1"]
RESOURCE = Path(__file__).resolve().parent.parent / "src/main/resources/openspec/machine-tool-markers.txt"

# Variables the installer reads in place of a home folder; unset so every probe sees only the throwaway home.
HOME_OVERRIDES = ["CODEX_HOME", "CLAUDE_CONFIG_DIR", "VIBE_HOME", "HERMES_HOME", "AUTOHAND_HOME", "GROK_HOME",
                  "SARVAM_HOME", "REASONIX_HOME", "APPDATA", "FLATPAK_XDG_CONFIG_HOME", "USERPROFILE"]

TEAM_CONFIG = "schema: spec-driven\ncontext: Team context marker.\nskillsScope: team\n"
EMPTY_CONFIG = "schema: spec-driven\nskillsScope:\n"


def env(home, **extra):
    e = isolated_env(home, **extra)
    for var in HOME_OVERRIDES:
        e.pop(var, None)
    return e


def run(version, cwd, home, args, extra=None, path=None):
    e = env(home, **(extra or {}))
    if path is not None:
        e["PATH"] = path
    return subprocess.run(cli(version) + args, cwd=cwd, env=e, capture_output=True, text=True,
                          stdin=subprocess.DEVNULL)


def ok(result, what):
    if result.returncode != 0:
        sys.exit(f"{what} failed ({result.returncode}):\n{result.stdout}\n{result.stderr}")
    return result


def recorded(p):
    """What a layout keeps of a file: the tool list's text; everything else only by name."""
    return p.read_text() if p.name == ".openspec-target" else ""


def layout(project, home):
    pf, pl = snapshot(project, skip=("openspec",), content=recorded)
    hf, hl = snapshot(home, skip=(".config", ".local", ".cache", ".npm", "xdg-config", ".agents/.skill-lock.json"),
                      content=recorded)
    out = {"project": pf, "home": hf}
    links = {side: l for side, l in (("project", pl), ("home", hl)) if l}
    if links:
        out["links"] = links
    if (home / ".agents/.skill-lock.json").is_file():
        # The lock names skills and sources, with timestamps that differ per run; record only that it exists.
        out["home"][".agents/.skill-lock.json"] = ""
    return out


def write_layout(version, case, project, home):
    text = sanitize(json.dumps(layout(project, home), indent=2, sort_keys=True) + "\n", project, home)
    write_fixture(FIXTURES / version / "skills-scope" / case / "layout.json", text)


def write_error(version, case, result, project, home):
    base = FIXTURES / version / "errors" / case
    write_fixture(base.with_suffix(".stdout"), sanitize(result.stdout, project, home))
    write_fixture(base.with_suffix(".stderr"), sanitize(result.stderr, project, home))
    write_fixture(base.with_suffix(".exit"), f"{result.returncode}\n")


def fresh(tmp, name):
    case = Path(tmp) / name
    project, home = case / "project", case / "home"
    project.mkdir(parents=True)
    home.mkdir()
    return project, home


def capture_21(version, tmp):
    pkg = package(version)

    # The config upstream's own update writes when a team keeps project skills.
    project, home = fresh(tmp, "remembered")
    ok(run(version, project, home, ["init", "--tools", "claude", "--scope", "project"]), "project init")
    remember = (pkg / "dist/core/project-skills-scope.js").as_uri()
    subprocess.run(["node", "--input-type=module", "-e",
                    "const m = await import(process.argv[1]); await m.rememberProjectSkills(process.argv[2]);",
                    "--", remember, str(project)], env=env(home), check=True, capture_output=True, text=True)
    write_fixture(FIXTURES / version / "skills-scope/remembered-config.yaml",
                  (project / "openspec/config.yaml").read_text())

    # An invalid value: warned about by every command that reads the config, whose other fields still load.
    (project / "openspec/config.yaml").write_text(TEAM_CONFIG)
    write_fixture(FIXTURES / version / "skills-scope/config-team.yaml", TEAM_CONFIG)
    ok(run(version, project, home, ["new", "change", "demo"]), "new change")
    team = run(version, project, home, ["instructions", "proposal", "--change", "demo", "--json"])
    write_fixture(FIXTURES / version / "skills-scope/team-warning.stderr", sanitize(team.stderr, project, home))
    write_fixture(FIXTURES / version / "skills-scope/team-warning.exit", f"{team.returncode}\n")
    context = json.loads(team.stdout).get("context")
    if context is None:
        sys.exit(f"{version}: instructions dropped the config's context alongside the skillsScope warning")
    write_fixture(FIXTURES / version / "skills-scope/team-context.txt", context.strip() + "\n")

    # An empty value: upstream checks only that the key is present, so it warns too.
    (project / "openspec/config.yaml").write_text(EMPTY_CONFIG)
    write_fixture(FIXTURES / version / "skills-scope/config-empty.yaml", EMPTY_CONFIG)
    empty = run(version, project, home, ["instructions", "proposal", "--change", "demo", "--json"])
    write_fixture(FIXTURES / version / "skills-scope/empty-warning.stderr", sanitize(empty.stderr, project, home))
    write_fixture(FIXTURES / version / "skills-scope/empty-warning.exit", f"{empty.returncode}\n")

    # What the setup wizard sends when no tool is detected: no tools, the project scope.
    project, home = fresh(tmp, "tools-none")
    none = run(version, project, home, ["init", "--tools", "none", "--scope", "project"])
    write_fixture(FIXTURES / version / "skills-scope/tools-none/exit", f"{none.returncode}\n")
    write_layout(version, "tools-none", project, home)
    if not (project / "openspec/config.yaml").is_file():
        sys.exit(f"{version}: init --tools none --scope project wrote no openspec/config.yaml")

    # The environment backstop: no --scope, the variable alone.
    project, home = fresh(tmp, "env-project")
    ok(run(version, project, home, ["init", "--tools", "claude"], {"OPENSPEC_SKILLS_SCOPE": "project"}), "env init")
    write_layout(version, "env-project", project, home)

    # A machine-scoped project, then a tool added without and with --scope.
    for case, extra in (("machine-add-keep", []), ("machine-add-project", ["--scope", "project"])):
        project, home = fresh(tmp, case)
        ok(run(version, project, home, ["init", "--tools", "cursor"]), "machine init")
        if case == "machine-add-keep":
            write_layout(version, "machine-only", project, home)
            # Not interactive and no --tools on a machine-scoped project: the CLI can't choose tools.
            write_error(version, "init-scope-project-no-tools",
                        run(version, project, home, ["init", "--scope", "project"]), project, home)
        ok(run(version, project, home, ["init", "--tools", "claude"] + extra), case)
        write_layout(version, case, project, home)

    # A project-scoped project keeps its scope when a tool is added with no scope.
    project, home = fresh(tmp, "project-add-keep")
    ok(run(version, project, home, ["init", "--tools", "cursor", "--scope", "project"]), "project init")
    ok(run(version, project, home, ["init", "--tools", "claude"]), "project add")
    write_layout(version, "project-add-keep", project, home)

    # The home folder: refused machine-wide, accepted for the project scope.
    _, home = fresh(tmp, "home-folder")
    refused = run(version, home, home, ["init", "--tools", "claude"])
    write_error(version, "init-home-global", refused, None, home)
    ok(run(version, home, home, ["init", "--tools", "claude", "--scope", "project"]), "home init")
    # The project is the home folder, so it is recorded once, as the home, without the CLI's own files.
    files, links = snapshot(home, skip=("openspec", ".config", ".local", ".cache", ".npm", ".agents/.skill-lock.json"),
                            content=recorded)
    doc = {"project": {}, "home": files}
    if links:
        doc["links"] = {"home": links}
    text = sanitize(json.dumps(doc, indent=2, sort_keys=True) + "\n", None, home)
    write_fixture(FIXTURES / version / "skills-scope/home-project/layout.json", text)

    if version == "2.1.0":
        # No git on PATH: 2.1.0 says so itself.
        project, home = fresh(tmp, "no-git")
        bin_dir = Path(tmp) / "node-only"
        bin_dir.mkdir(exist_ok=True)
        node = shutil.which("node")
        if not (bin_dir / "node").exists():
            os.symlink(node, bin_dir / "node")
        result = run(version, project, home, ["init", "--tools", "claude", "--scope", "project"], path=str(bin_dir))
        if result.returncode == 0:
            sys.exit("2.1.0 init succeeded without git on PATH")
        write_error(version, "init-no-git", result, project, home)


PROBE = r"""
import fs from 'node:fs';
import { syncBuiltinESMExports } from 'node:module';
const [skillsUrl, configUrl, cwd] = process.argv.slice(1);
const probed = [];
const original = fs.existsSync;
fs.existsSync = (p) => { probed.push(String(p)); return original(p); };
syncBuiltinESMExports();
const skills = await import(skillsUrl);
const config = await import(configUrl);
const agents = (await skills.detectAgents({ cwd })).agents.map(a => a.id);
const tools = {};
for (const t of config.AI_TOOLS) tools[t.value] = config.installerAgent(t.value).id;
console.log(JSON.stringify({ agents, probed: [...new Set(probed)], tools }));
"""


def probe(pkg, home, cwd):
    skills = pkg.parent.parent / "skills/dist/index.mjs"
    if not skills.is_file():
        sys.exit(f"no skills package beside {pkg}")
    e = env(home, XDG_CONFIG_HOME=str(home / "xdg-config"))
    out = subprocess.run(["node", "--input-type=module", "-e", PROBE, "--", skills.as_uri(),
                          (pkg / "dist/core/config.js").as_uri(), str(cwd)],
                         env=e, check=True, capture_output=True, text=True).stdout
    return json.loads(out)


def capture_markers(version, tmp):
    """Each path the installer checks, by its base, with the agents (and so the tools) it alone marks installed."""
    pkg = package(version)
    skills_version = json.loads((pkg.parent.parent / "skills/package.json").read_text())["version"]
    root = Path(tmp) / "markers"
    home, cwd = root / "home", root / "cwd"
    home.mkdir(parents=True)
    cwd.mkdir()
    base = probe(pkg, home, cwd)
    if base["agents"]:
        sys.exit(f"an empty home and project still detect {base['agents']}: this machine leaks into the probe")
    by_agent = {}
    for tool, agent in base["tools"].items():
        if agent != "universal":
            by_agent.setdefault(agent, []).append(tool)

    real_home, xdg = str(home), str(home / "xdg-config")
    entries = []
    for path in sorted(base["probed"]):
        if path.startswith(xdg + "/"):
            where, rel = "configHome", path[len(xdg) + 1:]
        elif path.startswith(real_home + "/"):
            where, rel = "home", path[len(real_home) + 1:]
        elif path.startswith(str(cwd) + "/"):
            entries.append({"base": "cwd", "path": path[len(str(cwd)) + 1:], "tickable": False,
                            "why": "a project path; the plugin's own project detection covers it"})
            continue
        else:
            entries.append({"base": "absolute", "path": path, "tickable": False,
                            "why": "outside the home folder, so not checked by the plugin"})
            continue
        # Only what this path adds: creating .gemini/antigravity also creates .gemini, which marks another agent.
        target = Path(path)
        target.parent.mkdir(parents=True, exist_ok=True)
        before = set(probe(pkg, home, cwd)["agents"])
        target.mkdir(exist_ok=True)
        agents = sorted(set(probe(pkg, home, cwd)["agents"]) - before)
        shutil.rmtree(home)
        home.mkdir()
        tools = sorted(t for a in agents for t in by_agent.get(a, []))
        entry = {"base": where, "path": rel, "agents": agents, "tools": tools, "tickable": bool(tools)}
        if not tools:
            entry["why"] = ("the folder alone marks no tool (the check needs a file or more than one path)"
                            if not agents else "its agent maps to no OpenSpec tool")
        entries.append(entry)
    doc = {"cliVersion": version, "skillsPackage": skills_version,
           "skipped": sorted(t for t, a in base["tools"].items() if a == "universal"),
           "entries": entries}
    write_fixture(FIXTURES / version / "skills-scope/machine-markers.json", json.dumps(doc, indent=2) + "\n")
    if version == RELEASES[-1]:
        # The plugin's copy: the tickable entries only, one per line, which MachineToolMarkersContractTest compares.
        lines = ["# Paths the OpenSpec CLI's skills installer checks to call a tool installed on this machine.",
                 f"# Written by scripts/capture-skills-scope.py from CLI {version} (skills {skills_version}); do not edit.",
                 "# base<TAB>path<TAB>tool ids. base is home, or configHome ($XDG_CONFIG_HOME, else ~/.config)."]
        lines += [f"{e['base']}\t{e['path']}\t{','.join(e['tools'])}" for e in entries if e["tickable"]]
        RESOURCE.write_text("\n".join(lines) + "\n")
    print(f"  {version}: {sum(e['tickable'] for e in entries)} tickable markers of {len(entries)} probed paths")


def capture_20(tmp):
    version = "2.0.0"
    layouts = []
    for case, extra in (("env-ignored", {"OPENSPEC_SKILLS_SCOPE": "project"}), ("no-env", {})):
        project, home = fresh(tmp, "20-" + case)
        ok(run(version, project, home, ["init", "--tools", "claude"], extra), case)
        layouts.append(sanitize(json.dumps(layout(project, home), indent=2, sort_keys=True) + "\n", project, home))
    if layouts[0] != layouts[1]:
        sys.exit("2.0.0 honoured OPENSPEC_SKILLS_SCOPE: the layouts differ")
    write_fixture(FIXTURES / version / "skills-scope/env-ignored/layout.json", layouts[0])


def main():
    wanted = os.environ.get("CAPTURE_VERSIONS")
    versions = [v for v in RELEASES if not wanted or v in wanted.split(",")]
    if "2.1.0" in versions:
        require_git_and_network()
    for version in versions:
        print(version)
        with tempfile.TemporaryDirectory() as tmp:
            if key(version) >= key("2.1.0"):
                capture_21(version, tmp)
                capture_markers(version, tmp)
            else:
                capture_20(tmp)


if __name__ == "__main__":
    main()
