#!/usr/bin/env python3
"""Capture the state Fast-Forward starts from: a change just created by `openspec new change`, as contract fixtures.

Records, under src/test/resources/fixtures/cli/<version>/new-change/:

    status.json                  `openspec status --change demo --json` (proposal ready, the rest blocked)
    instructions-proposal.json   `openspec instructions proposal --change demo --json`

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
    leaks = subprocess.run(["grep", "-rnE", "/Users/|/private/|/var/folders", str(out)],
                           capture_output=True, text=True).stdout
    if leaks:
        sys.exit("machine paths leaked into the fixture:\n" + leaks)
    print(f"captured {out.relative_to(REPO)} from CLI {version}")


if __name__ == "__main__":
    main()
