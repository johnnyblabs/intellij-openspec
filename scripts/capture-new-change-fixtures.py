#!/usr/bin/env python3
"""Capture the state Fast-Forward starts from: a change just created by `openspec new change`, as contract fixtures.

Records, under src/test/resources/fixtures/cli/<version>/new-change/:

    status.json                  `openspec status --change demo --json` (proposal ready, the rest blocked)
    instructions-proposal.json   `openspec instructions proposal --change demo --json`

and, under <version>/new-change-past-apply/, a change on a schema forked from spec-driven with one more artifact,
`retro`, that requires `tasks` and sits outside the apply gate (`apply.requires: [tasks]`). It shows where upstream's
propose and ff stop: they generate the apply-required artifacts and leave `retro`.

    status-step<N>.json          `status --change retro-demo --json` after the first N artifacts are written
                                 (0: none; 4: proposal, specs, design and tasks, so apply-ready but not complete;
                                 5: retro too, so complete)
    instructions-<id>.json       `instructions <id> --change retro-demo --json` for each of the five artifacts

The run uses an isolated HOME and XDG directories, so the real global OpenSpec state is untouched.
Machine paths are replaced with /fixture/demo-project.

Usage: scripts/capture-new-change-fixtures.py   (uses the `openspec` on PATH, or $OPENSPEC_BIN)
"""
import os
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OPENSPEC = os.environ.get("OPENSPEC_BIN", "openspec").split()

# An artifact after tasks and outside the apply gate, appended to a fork of spec-driven.
RETRO_ARTIFACT = """
  - id: retro
    generates: retro.md
    description: A retrospective, written after the tasks
    template: design.md
    instruction: |
      Write a short retrospective.
    requires:
      - tasks
"""

# The artifacts in schema order (the apply-required four, then retro), written one by one between status captures.
ARTIFACT_FILES = [
    ("proposal.md", "## Why\nA demo change.\n\n## What Changes\n- Demo\n\n## Capabilities\n### New Capabilities\n"
                    "- `demo`\n\n## Impact\nNone.\n"),
    ("specs/demo/spec.md", "## ADDED Requirements\n\n### Requirement: Demo\nThe system SHALL demo.\n\n"
                           "#### Scenario: Demo\n- **WHEN** asked\n- **THEN** it SHALL demo\n"),
    ("design.md", "## Context\nContext.\n\n## Decisions\nDecisions.\n"),
    ("tasks.md", "## 1. Do\n- [ ] 1.1 Demo\n"),
    ("retro.md", "# Retro\nWent fine.\n"),
]


def main():
    version = subprocess.run([*OPENSPEC, "--version"], capture_output=True, text=True).stdout.strip()
    out = REPO / "src/test/resources/fixtures/cli" / version / "new-change"
    out.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        project = work / "project"
        (project / "openspec").mkdir(parents=True)
        (project / "openspec" / "config.yaml").write_text("schema: spec-driven\n")
        env_root = work / "env"
        for d in ("home", "cfg", "data", "state", "cache"):
            (env_root / d).mkdir(parents=True)
        env = dict(os.environ, HOME=str(env_root / "home"), XDG_CONFIG_HOME=str(env_root / "cfg"),
                   XDG_DATA_HOME=str(env_root / "data"), XDG_STATE_HOME=str(env_root / "state"),
                   XDG_CACHE_HOME=str(env_root / "cache"), OPENSPEC_TELEMETRY="0", NO_COLOR="1", CI="1")
        roots = sorted({str(project), os.path.realpath(project)}, key=len, reverse=True)

        def run(*args):
            p = subprocess.run([*OPENSPEC, *args], cwd=project, env=env, capture_output=True, text=True)
            if p.returncode != 0:
                sys.exit(f"{' '.join(args)} failed ({p.returncode}):\n{p.stdout}{p.stderr}")
            text = p.stdout
            for r in roots:
                text = text.replace(r, "/fixture/demo-project")
            return text

        run("new", "change", "demo")
        (out / "status.json").write_text(run("status", "--change", "demo", "--json"))
        (out / "instructions-proposal.json").write_text(run("instructions", "proposal", "--change", "demo", "--json"))

        past = out.parent / "new-change-past-apply"
        past.mkdir(parents=True, exist_ok=True)
        run("schema", "fork", "spec-driven", "with-retro")
        schema = project / "openspec/schemas/with-retro/schema.yaml"
        text = schema.read_text()
        if "\napply:\n" not in text:
            sys.exit("the forked schema has no top-level apply: section to insert before")
        schema.write_text(text.replace("\napply:\n", RETRO_ARTIFACT + "\napply:\n", 1))
        run("new", "change", "retro-demo", "--schema", "with-retro")
        change = project / "openspec/changes/retro-demo"
        (past / "status-step0.json").write_text(run("status", "--change", "retro-demo", "--json"))
        for step, (rel, content) in enumerate(ARTIFACT_FILES, start=1):
            (change / rel).parent.mkdir(parents=True, exist_ok=True)
            (change / rel).write_text(content)
            (past / f"status-step{step}.json").write_text(run("status", "--change", "retro-demo", "--json"))
        for artifact in ("proposal", "specs", "design", "tasks", "retro"):
            (past / f"instructions-{artifact}.json").write_text(
                run("instructions", artifact, "--change", "retro-demo", "--json"))
    for folder in (out, out.parent / "new-change-past-apply"):
        leaks = subprocess.run(["grep", "-rnE", "/Users/|/private/|/var/folders", str(folder)],
                               capture_output=True, text=True).stdout
        if leaks:
            sys.exit("machine paths leaked into the fixture:\n" + leaks)
    print(f"captured {out.relative_to(REPO)} from CLI {version}")


if __name__ == "__main__":
    main()
