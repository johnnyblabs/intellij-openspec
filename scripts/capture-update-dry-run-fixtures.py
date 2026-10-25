#!/usr/bin/env python3
"""Capture real `openspec update --dry-run --json` runs as contract fixtures for the plugin's update preview.

For every case this records, under src/test/resources/fixtures/cli/<version>/update-dry-run/:

    <case>.json     stdout of the dry run (the plan, or the failure envelope)
    <case>.exit     its exit code
    <case>.stderr   its stderr (only when non-empty)

Each case gets a fresh project and a fresh isolated HOME, with XDG_* unset, CI=1 and OPEN_SPEC_INTERACTIVE=0, so the
real global OpenSpec state is untouched and the CLI takes its non-interactive path. Before and after every dry run the
script hashes the project and HOME trees and stops if anything changed: the dry run must write nothing.

Machine paths are replaced with /fixture/demo-project (the project) and /fixture/home (HOME).

Usage: scripts/capture-update-dry-run-fixtures.py                    (uses the `openspec` on PATH, or $OPENSPEC_BIN)
       OPENSPEC_BIN="npx -y @fission-ai/openspec@1.14.3" scripts/capture-update-dry-run-fixtures.py --older
                                                                     (only the unknown-option case, for an older CLI)
       STALE_BIN="npx -y @fission-ai/openspec@1.14.3"                (the CLI that seeds the stale-skills case;
                                                                     defaults to that npx pin)
"""
import hashlib
import os
import shutil
import stat
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OPENSPEC = os.environ.get("OPENSPEC_BIN", "openspec").split()
STALE = os.environ.get("STALE_BIN", "npx -y @fission-ai/openspec@1.14.3").split()

LEGACY_CURSOR_FILES = ["openspec-proposal.md", "openspec-apply.md", "openspec-archive.md"]


def env_for(home: Path) -> dict:
    env = {k: v for k, v in os.environ.items() if not k.startswith("XDG_")}
    # npx caches packages under HOME; keep that out of the no-writes check (and out of the fixtures).
    env.setdefault("npm_config_cache", str(Path(tempfile.gettempdir()) / "osp-dryrun-npm-cache"))
    env.update(HOME=str(home), CI="1", OPEN_SPEC_INTERACTIVE="0", OPENSPEC_TELEMETRY="0",
               NO_COLOR="1", FORCE_COLOR="0", OPENSPEC_NO_UPDATE_CHECK="1")
    if os.name == "nt":
        env.update(USERPROFILE=str(home), APPDATA=str(home / "AppData" / "Roaming"))
    return env


def run(cmd, cwd: Path, home: Path, check=True):
    result = subprocess.run(cmd, cwd=cwd, env=env_for(home), capture_output=True, text=True, stdin=subprocess.DEVNULL)
    if check and result.returncode != 0:
        sys.exit(f"setup step failed: {' '.join(cmd)}\n{result.stdout}\n{result.stderr}")
    return result


def tree_hash(*roots: Path) -> str:
    h = hashlib.sha256()
    for root in roots:
        for p in sorted(root.rglob("*")):
            if p.is_symlink():
                h.update(f"L {p} -> {os.readlink(p)}\n".encode())
            elif p.is_file():
                h.update(f"F {p} {p.stat().st_mode & 0o777}\n".encode())
                h.update(p.read_bytes())
            elif p.is_dir():
                h.update(f"D {p}\n".encode())
    return h.hexdigest()


# --- Case setups: each takes (project, home) and leaves the project ready for the dry run ---

def init(project, home, *tools, cli=None):
    run((cli or OPENSPEC) + ["init", "--tools", ",".join(tools), "."], project, home)


def global_config(home: Path) -> Path:
    return home / ".config" / "openspec" / "config.json"


def up_to_date(project, home):
    init(project, home, "claude")
    run(OPENSPEC + ["update"], project, home)


def first_run_global_config(project, home):
    up_to_date(project, home)
    global_config(home).unlink()


def stale_skills(project, home):
    init(project, home, "claude", cli=STALE)
    run(OPENSPEC + ["update"], project, home)  # migrates the global config, so only the skills are stale
    init(project, home, "claude", cli=STALE)  # re-scaffold the older skills over the refreshed ones


def legacy_pending(project, home):
    up_to_date(project, home)
    init(project, home, "cursor")
    commands = project / ".cursor" / "commands"
    commands.mkdir(parents=True, exist_ok=True)
    for name in LEGACY_CURSOR_FILES:
        (commands / name).write_text(f"# {name}\n\nA legacy command file.\n")


def windsurf_move(project, home):
    up_to_date(project, home)
    init(project, home, "devin")
    shutil.move(str(project / ".devin"), str(project / ".windsurf"))


def read_only_failure(project, home):
    """One stale skill made read-only: the planner probes write access on the file, as the real write would fail."""
    stale_skills(project, home)
    (project / ".claude" / "skills" / "openspec-propose" / "SKILL.md").chmod(stat.S_IRUSR | stat.S_IRGRP | stat.S_IROTH)


def no_openspec_dir(project, home):
    pass


CASES = {
    "up-to-date": (up_to_date, ["--dry-run", "--json"]),
    "first-run-global-config": (first_run_global_config, ["--dry-run", "--json"]),
    "stale-skills": (stale_skills, ["--dry-run", "--json"]),
    "legacy-pending": (legacy_pending, ["--dry-run", "--json"]),
    "legacy-pending-force": (legacy_pending, ["--dry-run", "--force", "--json"]),
    "windsurf-move": (windsurf_move, ["--dry-run", "--json"]),
    "read-only-failure": (read_only_failure, ["--dry-run", "--json"]),
    "no-openspec-dir": (no_openspec_dir, ["--dry-run", "--json"]),
    "json-without-dry-run": (up_to_date, ["--json"]),
}
OLDER_CASES = {"update-dry-run-unknown-option": (up_to_date, ["--dry-run", "--json"])}


def sanitize(text: str, project: Path, home: Path) -> str:
    for real, fake in ((project, "/fixture/demo-project"), (home, "/fixture/home")):
        for form in {str(real), str(real.resolve())}:
            text = text.replace(form, fake)
    return text


def unlock(root: Path):
    for p in [root, *root.rglob("*")]:
        if not p.is_symlink():
            p.chmod(stat.S_IRWXU if p.is_dir() else stat.S_IRUSR | stat.S_IWUSR)


def main():
    older = "--older" in sys.argv
    version = run(OPENSPEC + ["--version"], Path.cwd(), Path(tempfile.mkdtemp()), check=True).stdout.strip()
    out = REPO / "src/test/resources/fixtures/cli" / version
    if not older:
        out = out / "update-dry-run"
    out.mkdir(parents=True, exist_ok=True)
    for name, (setup, args) in (OLDER_CASES if older else CASES).items():
        scratch = Path(tempfile.mkdtemp(prefix="osp-dryrun-"))
        project, home = scratch / "demo-project", scratch / "home"
        project.mkdir()
        home.mkdir()
        try:
            setup(project, home)
            before = tree_hash(project, home)
            result = run(OPENSPEC + ["update"] + args, project, home, check=False)
            if tree_hash(project, home) != before:
                sys.exit(f"{name}: the dry run changed the project or HOME")
            (out / f"{name}.json").write_text(sanitize(result.stdout, project, home))
            (out / f"{name}.exit").write_text(f"{result.returncode}\n")
            stderr = sanitize(result.stderr, project, home).strip()
            if stderr:
                (out / f"{name}.stderr").write_text(stderr + "\n")
            print(f"{name}: exit {result.returncode}, {len(result.stdout)} bytes")
        finally:
            unlock(scratch)
            shutil.rmtree(scratch, ignore_errors=True)


if __name__ == "__main__":
    main()
