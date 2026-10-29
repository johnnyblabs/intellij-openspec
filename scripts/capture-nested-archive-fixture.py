#!/usr/bin/env python3
"""Capture a real `openspec archive` of a change with nested capabilities, as a contract fixture for Sync Specs.

The project has one existing nested main spec (identity/sessions). The change (`c1`) adds to a flat capability (flat),
modifies the existing nested one, adds a new nested one (identity/user-auth), adds to its parent folder's own
capability (identity, a folder holding both a spec.md and nested capabilities), and has a delta in a dot-folder
(.hidden), which the archive ignores. It records, under
src/test/resources/fixtures/cli/<version>/archive-nested/:

    before/openspec/specs/...      the main specs before archiving
    change/specs/...               the change's delta specs, at their nested paths
    after/openspec/specs/...       the main specs `openspec archive c1 --yes` wrote
    archive-human.txt              the archive output (lists capabilities in the order the CLI processed them)

The run uses an isolated HOME and XDG directories, so the real global OpenSpec state is untouched.
Machine paths are replaced with /fixture and the archive date with <DATE>.

Usage: scripts/capture-nested-archive-fixture.py   (uses the `openspec` on PATH, or $OPENSPEC_BIN)
"""
import os
import shutil
import subprocess
import sys
import tempfile
from datetime import date
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OPENSPEC = os.environ.get("OPENSPEC_BIN", "openspec").split()

PROPOSAL = """## Why

A probe change used to capture how the archive merges nested capability deltas into main specs.

## What Changes

- Add requirements to a flat and two nested capabilities.
"""


def req(name, body, scenario):
    return (f"### Requirement: {name}\n{body}\n\n#### Scenario: {scenario}\n"
            f"- **WHEN** it is used\n- **THEN** it SHALL behave\n")


MAIN = {
    "identity/sessions": "# identity/sessions Specification\n\n## Purpose\n"
                         "Session handling used to capture nested archive behavior in fixtures.\n\n## Requirements\n\n"
                         + req("Session expiry", "Sessions SHALL expire.", "Expire"),
}
DELTA = {
    "flat": "## ADDED Requirements\n\n" + req("Flat thing", "The system SHALL do the flat thing.", "Flat"),
    "identity/sessions": "## MODIFIED Requirements\n\n"
                         + req("Session expiry", "Sessions SHALL expire after 30 minutes idle.", "Expire"),
    "identity": "## ADDED Requirements\n\n" + req("Identity root", "Identity SHALL have a root.", "Root"),
    ".hidden": "## ADDED Requirements\n\n" + req("Hidden", "This SHALL be ignored.", "Ignored"),
    "identity/user-auth": "## ADDED Requirements\n\n" + req("Login", "Users SHALL log in.", "Login"),
}


def write(root, rel, text):
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def main():
    version = subprocess.run([*OPENSPEC, "--version"], capture_output=True, text=True).stdout.strip()
    out = REPO / "src/test/resources/fixtures/cli" / version / "archive-nested"
    if out.exists():
        shutil.rmtree(out)
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        project = work / "project"
        env_root = work / "env"
        for d in ("home", "cfg", "data", "state", "cache"):
            (env_root / d).mkdir(parents=True)
        env = dict(os.environ, HOME=str(env_root / "home"), XDG_CONFIG_HOME=str(env_root / "cfg"),
                   XDG_DATA_HOME=str(env_root / "data"), XDG_STATE_HOME=str(env_root / "state"),
                   XDG_CACHE_HOME=str(env_root / "cache"), OPENSPEC_TELEMETRY="0", NO_COLOR="1", CI="1")
        change = project / "openspec" / "changes" / "c1"
        write(project, "openspec/config.yaml", "schema: spec-driven\n")
        write(change, ".openspec.yaml", "schema: spec-driven\ncreated: 2026-10-28\n")
        write(change, "proposal.md", PROPOSAL)
        write(change, "tasks.md", "## 1. Work\n\n- [x] 1.1 Done\n")
        for cap, text in MAIN.items():
            write(project, f"openspec/specs/{cap}/spec.md", text)
            write(out / "before", f"openspec/specs/{cap}/spec.md", text)
        for cap, text in DELTA.items():
            write(change, f"specs/{cap}/spec.md", text)
            write(out / "change", f"specs/{cap}/spec.md", text)

        p = subprocess.run([*OPENSPEC, "archive", "c1", "--yes"], cwd=project, env=env,
                           capture_output=True, text=True)
        if p.returncode != 0:
            sys.exit(f"archive failed ({p.returncode}):\n{p.stdout}{p.stderr}")
        text = p.stdout + p.stderr
        for root in sorted({str(project), os.path.realpath(project)}, key=len, reverse=True):
            text = text.replace(root, "/fixture")
        (out / "archive-human.txt").write_text(text.replace(date.today().isoformat(), "<DATE>"))
        for spec in sorted((project / "openspec" / "specs").rglob("spec.md")):
            write(out / "after", spec.relative_to(project).as_posix(), spec.read_text())
    (out.parent / "version.txt").exists() or (out.parent / "version.txt").write_text(version + "\n")
    leaks = subprocess.run(["grep", "-rnE", "/Users/|/private/|/var/folders", str(out)],
                           capture_output=True, text=True).stdout
    if leaks:
        sys.exit("machine paths leaked into the fixture:\n" + leaks)
    print(f"captured {out.relative_to(REPO)} from CLI {version}")


if __name__ == "__main__":
    main()
