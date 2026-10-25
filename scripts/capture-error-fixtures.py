#!/usr/bin/env python3
"""Capture real OpenSpec CLI failure output as contract fixtures for the plugin's CLI response reader.

For every case this records, under src/test/resources/fixtures/cli/<version>/errors/:

    <case>.stdout   the command's stdout
    <case>.stderr   the command's stderr
    <case>.exit     the exit code

Runs use the environment the plugin gives the CLI (telemetry, colour and update notices off) unless a case
says otherwise, and an isolated HOME and XDG directories, so the real global OpenSpec state is untouched.
Machine paths are replaced with /fixture and the config's anonymous id with a fixed placeholder.

Usage: scripts/capture-error-fixtures.py   (uses the `openspec` on PATH, or $OPENSPEC_BIN)
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OPENSPEC = os.environ.get("OPENSPEC_BIN", "openspec").split()

PLUGIN_ENV = {"OPENSPEC_TELEMETRY": "0", "NO_COLOR": "1", "FORCE_COLOR": "0", "OPENSPEC_NO_UPDATE_CHECK": "1"}
COLOUR_ENV = {"OPENSPEC_TELEMETRY": "0", "FORCE_COLOR": "1", "OPENSPEC_NO_UPDATE_CHECK": "1"}

PROPOSAL = """## Why
A probe change used to capture how the CLI reports failures.

## What Changes
- Change the auth capability.

## Capabilities
### Modified Capabilities
- `auth`: probe.

## Impact
None.
"""

AUTH = """# auth Specification

## Purpose
Authenticates users against the identity store for the system.

## Requirements
### Requirement: Login
The system SHALL authenticate users.

#### Scenario: ok
- **WHEN** valid credentials are given
- **THEN** the user is logged in
"""


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def seed_project(root: Path) -> None:
    write(root / "openspec/config.yaml", "schema: spec-driven\n")
    write(root / "openspec/specs/auth/spec.md", AUTH)
    write(root / "openspec/changes/good/.openspec.yaml", "schema: spec-driven\ncreated: 2026-10-17\n")
    write(root / "openspec/changes/good/proposal.md", PROPOSAL)
    write(root / "openspec/changes/good/tasks.md", "- [ ] 1.1 do it\n- [x] 1.2 done\n")
    write(root / "openspec/changes/badyaml/.openspec.yaml", "schema: [spec-driven\n  created: :::\n")
    write(root / "openspec/changes/badyaml/proposal.md", PROPOSAL)
    write(root / "openspec/changes/noproposal/.openspec.yaml", "schema: spec-driven\ncreated: 2026-10-17\n")


class Capture:
    def __init__(self, base: Path, out: Path):
        self.base = base
        self.out = out
        self.home = {
            "HOME": str(base / "home"),
            "XDG_CONFIG_HOME": str(base / "xdg-config"),
            "XDG_DATA_HOME": str(base / "xdg-data"),
            "XDG_STATE_HOME": str(base / "xdg-state"),
            "XDG_CACHE_HOME": str(base / "xdg-cache"),
        }
        for d in self.home.values():
            Path(d).mkdir(parents=True, exist_ok=True)

    def run(self, cwd: Path, *args: str, env: dict = None):
        e = {k: v for k, v in os.environ.items() if k not in ("NO_COLOR", "FORCE_COLOR", "CI", "TERM", "COLORTERM")}
        e.update(self.home)
        e.update(PLUGIN_ENV if env is None else env)
        return subprocess.run(OPENSPEC + list(args), cwd=cwd, env=e, capture_output=True, text=True,
                              stdin=subprocess.DEVNULL)

    def sanitize(self, text: str) -> str:
        for root in sorted({str(self.base.resolve()), str(self.base)}, key=len, reverse=True):
            text = text.replace(root, "/fixture")
        return re.sub(r'("anonymousId":\s*")[0-9a-f-]{36}"', r'\g<1>00000000-0000-0000-0000-000000000000"', text)

    def case(self, name: str, cwd: Path, *args: str, env: dict = None) -> None:
        r = self.run(cwd, *args, env=env)
        (self.out / f"{name}.stdout").write_text(self.sanitize(r.stdout))
        (self.out / f"{name}.stderr").write_text(self.sanitize(r.stderr))
        (self.out / f"{name}.exit").write_text(f"{r.returncode}\n")
        print(f"{name:34} exit={r.returncode} stdout={len(r.stdout)}B stderr={len(r.stderr)}B")


def seed_legacy(root: Path, cap: Capture) -> None:
    root.mkdir(parents=True)
    r = cap.run(root, "init", "--tools", "cursor", ".")
    if r.returncode != 0:
        sys.exit(f"init failed: {r.stderr}")
    for name in ("proposal", "apply", "archive"):
        write(root / f".cursor/commands/openspec-{name}.md", f"# legacy {name}\n")


def main() -> None:
    version = subprocess.run(OPENSPEC + ["--version"], capture_output=True, text=True).stdout.strip()
    out = REPO / "src/test/resources/fixtures/cli" / version / "errors"
    out.mkdir(parents=True, exist_ok=True)
    (out / "version.txt").write_text(version + "\n")

    with tempfile.TemporaryDirectory(prefix="openspec-errors-") as tmp:
        base = Path(tmp)
        cap = Capture(base, out)
        empty = base / "empty"
        empty.mkdir()
        proj = base / "proj"
        seed_project(proj)

        # No OpenSpec root, no registered stores.
        cap.case("validate-noroot", empty, "validate", "--all", "--json")
        cap.case("list-noroot", empty, "list", "--json")
        cap.case("update-fail", empty, "update")
        cap.case("config-list-default", empty, "config", "list", "--json")

        # Item and change errors inside a root.
        cap.case("validate-unknown", proj, "validate", "nosuch", "--json")
        cap.case("show-error", proj, "show", "noproposal", "--json")
        # The Browse preview's exact args (ChangeDeltaModel.changeShowArgs): a renamed or deleted change, and a change
        # with no proposal yet.
        cap.case("show-change-missing", proj, "show", "nosuch", "--type", "change", "--json")
        cap.case("show-change-noproposal", proj, "show", "noproposal", "--type", "change", "--json")
        # A successful show, for its stderr: 1.3-1.5 print a flags warning there even on success.
        cap.case("show-change-good", proj, "show", "good", "--type", "change", "--json")
        cap.case("status-unknown", proj, "status", "--change", "nosuch", "--json")
        cap.case("status-badyaml", proj, "status", "--change", "badyaml", "--json")
        cap.case("instructions-unknown-artifact", proj, "instructions", "bogusart", "--change", "good", "--json")
        cap.case("archive-unknown", proj, "archive", "nosuch", "--yes", "--json")

        # The same legacy-cleanup project, captured plain and coloured. Each gets its own fresh global state,
        # because the first `update` also migrates the global config and prints that, which a second run would not.
        for variant, env in (("plain", None), ("ansi", COLOUR_ENV)):
            vcap = Capture(base / f"legacy-{variant}", out)
            project = base / f"legacy-{variant}" / "project"
            seed_legacy(project, vcap)
            vcap.case(f"update-legacy-pending-{variant}", project, "update", env=env)

        # A custom workflow profile.
        r1 = cap.run(empty, "config", "set", "profile", "custom")
        r2 = cap.run(empty, "config", "set", "workflows", '["propose","explore","apply","archive","verify"]')
        print(f"config set profile -> {r1.returncode}; workflows -> {r2.returncode} {r2.stderr.strip()[:120]}")
        cap.case("config-list-custom", empty, "config", "list", "--json")

        # A registered store, then its root removed.
        store = base / "storeA"
        r = cap.run(base, "store", "setup", "storea", "--path", str(store), "--json")
        print(f"store setup -> {r.returncode}")
        cap.case("list-noroot-withstores", empty, "list", "--json")
        cap.case("validate-noroot-withstores", empty, "validate", "--all", "--json")
        shutil.rmtree(store / "openspec")
        cap.case("doctor-store-missing-root", proj, "doctor", "--store", "storea", "--json")
        cap.case("store-doctor-missing-root", proj, "store", "doctor", "storea", "--json")


if __name__ == "__main__":
    main()
