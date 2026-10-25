#!/usr/bin/env python3
"""Capture what `openspec init --tools <id>` writes for the explore workflow, as contract fixtures for Explore.

For every tool in fixtures/cli/<version>/ai-tools.json and each delivery mode (both, skills, commands), this runs
`openspec init --tools <id>` in a fresh project with a fresh isolated HOME and XDG_* dirs (telemetry off, CI=1), and
records every file whose path mentions "explore", under src/test/resources/fixtures/cli/<version>/explore/:

    listing-<delivery>.txt   one line per file: "<tool id> P <path>" in the project, "<tool id> H <path>" in HOME

It also keeps a few real generated files, with machine paths replaced by /fixture/home:

    claude-skill-both.md, claude-skill-skills.md   .claude/skills/openspec-explore/SKILL.md under each delivery
    claude-skill-explore-only.md                   the same skill for a custom profile with only the explore workflow
    claude-command.md                              .claude/commands/opsx/explore.md
    cursor-command.md                              .cursor/commands/opsx-explore.md
    gemini-command.toml                            .gemini/commands/opsx/explore.toml
    agents-skill.md, agents-openspec-target.txt    the shared .agents skill (codex) and its owner marker

The real global OpenSpec state is never touched.

Usage: scripts/capture-explore-fixtures.py        (uses the `openspec` on PATH, or $OPENSPEC_BIN)
"""
import json
import os
import shlex
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DELIVERIES = ("both", "skills", "commands")


def cli() -> list:
    return shlex.split(os.environ.get("OPENSPEC_BIN", "openspec"))


def run(args, cwd, env):
    return subprocess.run(cli() + args, cwd=cwd, env=env, capture_output=True, text=True)


def isolated_env(home: Path) -> dict:
    env = {k: v for k, v in os.environ.items() if not k.startswith("XDG_")}
    env.update(HOME=str(home), XDG_CONFIG_HOME=str(home / ".config"), XDG_DATA_HOME=str(home / ".local/share"),
               XDG_STATE_HOME=str(home / ".local/state"), XDG_CACHE_HOME=str(home / ".cache"),
               OPENSPEC_TELEMETRY="0", DO_NOT_TRACK="1", CI="1", OPEN_SPEC_INTERACTIVE="0")
    home.mkdir(parents=True, exist_ok=True)
    return env


def init_project(tmp: Path, tool: str, delivery: str, workflows=None) -> tuple:
    project, home = tmp / f"{delivery}-{tool}" / "project", tmp / f"{delivery}-{tool}" / "home"
    project.mkdir(parents=True)
    env = isolated_env(home)
    run(["config", "set", "delivery", delivery], project, env)
    if workflows is not None:
        run(["config", "set", "profile", "custom"], project, env)
        run(["config", "set", "workflows", json.dumps(workflows)], project, env)
    result = run(["init", "--tools", tool, "--no-copilot-cloud", "--no-animation", "."], project, env)
    if result.returncode != 0:
        sys.exit(f"init --tools {tool} ({delivery}) failed:\n{result.stdout}\n{result.stderr}")
    return project, home


def explore_files(root: Path, skip_openspec: bool) -> list:
    found = []
    for path in sorted(root.rglob("*")):
        rel = path.relative_to(root)
        if skip_openspec and rel.parts and rel.parts[0] == "openspec":
            continue
        if path.is_file() and "explore" in str(rel).lower():
            found.append(rel.as_posix())
    return found


def sanitize(text: str, home: Path) -> str:
    return text.replace(str(home), "/fixture/home")


def main() -> None:
    version = run(["--version"], REPO, os.environ.copy()).stdout.strip()
    tools = [t["value"] for t in json.loads((REPO / f"src/test/resources/fixtures/cli/{version}/ai-tools.json")
                                           .read_text())["tools"]]
    out = REPO / f"src/test/resources/fixtures/cli/{version}/explore"
    out.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="openspec-explore-") as tmp_name:
        tmp = Path(tmp_name)
        for delivery in DELIVERIES:
            lines = []
            for tool in tools:
                project, home = init_project(tmp, tool, delivery)
                lines += [f"{tool} P {p}" for p in explore_files(project, skip_openspec=True)]
                lines += [f"{tool} H {p}" for p in explore_files(home, skip_openspec=False)
                          if not p.startswith((".config/", ".local/", ".cache/"))]
            (out / f"listing-{delivery}.txt").write_text("\n".join(lines) + "\n")

        def keep(delivery, tool, rel, name, home_based=False):
            project, home = tmp / f"{delivery}-{tool}" / "project", tmp / f"{delivery}-{tool}" / "home"
            source = (home if home_based else project) / rel
            (out / name).write_text(sanitize(source.read_text(), home))

        keep("both", "claude", ".claude/skills/openspec-explore/SKILL.md", "claude-skill-both.md")
        keep("skills", "claude", ".claude/skills/openspec-explore/SKILL.md", "claude-skill-skills.md")
        keep("both", "claude", ".claude/commands/opsx/explore.md", "claude-command.md")
        keep("both", "cursor", ".cursor/commands/opsx-explore.md", "cursor-command.md")
        keep("both", "gemini", ".gemini/commands/opsx/explore.toml", "gemini-command.toml")
        keep("both", "codex", ".agents/skills/openspec-explore/SKILL.md", "agents-skill.md")
        keep("both", "codex", ".agents/skills/.openspec-target", "agents-openspec-target.txt")

        project, home = init_project(tmp / "explore-only", "claude", "both", workflows=["explore"])
        (out / "claude-skill-explore-only.md").write_text(
            sanitize((project / ".claude/skills/openspec-explore/SKILL.md").read_text(), home))

    print(f"Wrote explore fixtures for {len(tools)} tools to {out.relative_to(REPO)}")


if __name__ == "__main__":
    main()
