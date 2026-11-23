#!/usr/bin/env python3
"""Capture the small per-version CLI fixtures that no family script owns, for each version named.

Under src/test/resources/fixtures/cli/<version>/ it writes:

    version.txt                          `openspec --version`
    config-list.json                     `openspec config list --json` after `config profile core`
                                         (the telemetry id replaced with a fixed placeholder)
    home-state/projects.json             the known-projects record 2.1+ writes under XDG_DATA_HOME after a project
                                         init, with the project and HOME paths sanitized (absent before 2.1)
    errors/init-scope-project.*          `init --tools claude --scope project`: rejected before 2.1.0
    errors/init-amazon-q.*               `init --tools amazon-q`: Amazon Q is retired in 2.0
    validate-parity-corpus.json          `validate --all --json` over the committed 1.6.0 parity corpus
    validate-parity-corpus-strict.json   the same with --strict
    instructions/instructions-{design,specs}.json
                                         `instructions <artifact> --change demo --json` in a project made by
                                         `init --tools none` and `new change demo`, with a filled proposal.md
                                         (the 1.15.0 recipe), project path sanitized

Each errors/ case is `<case>.stdout`, `<case>.stderr` and `<case>.exit`. Every run uses an isolated HOME and XDG
directories and a package installed into a scratch prefix (scripts/lib/openspec_capture.py), never npx. A file is
written only after it is checked for machine paths.

Usage: scripts/capture-cli-generation.py 2.0.0 2.1.0 2.1.1 [1.17.0 ...]
"""
import re
import shutil
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "scripts" / "lib"))
from openspec_capture import cli, integrity, key, run, sanitize, write_fixture  # noqa: E402

FIXTURES = REPO / "src/test/resources/fixtures/cli"
CORPUS = FIXTURES / "1.6.0" / "parity-corpus" / "openspec"
PROPOSAL = """## Why

Users need a way to export their data.

## What Changes

- Add a data export capability.

## Capabilities

### New Capabilities

- `data-export`: export a user's data as a file.

## Impact

New export command.
"""
ANON = re.compile(r'("anonymousId":\s*")[0-9a-f-]{36}"')


def capture(version):
    out = FIXTURES / version
    osp = cli(version)
    with tempfile.TemporaryDirectory(prefix="osp-gen-") as tmp:
        base = Path(tmp)
        home, project = base / "home", base / "demo-project"
        home.mkdir()
        project.mkdir()

        def fixture(rel, text):
            write_fixture(out / rel, sanitize(text, project, home))

        fixture("version.txt", run(osp + ["--version"], project, home).stdout)

        run(osp + ["config", "profile", "core"], project, home)
        listed = run(osp + ["config", "list", "--json"], project, home).stdout
        fixture("config-list.json", ANON.sub(r'\g<1>00000000-0000-0000-0000-000000000000"', listed))

        def error_case(name, *args):
            work = base / name
            work.mkdir()
            r = run(osp + list(args), work, home, check=False)
            for ext, text in (("stdout", r.stdout), ("stderr", r.stderr), ("exit", f"{r.returncode}\n")):
                write_fixture(out / "errors" / f"{name}.{ext}", sanitize(text, work, home))
            print(f"  errors/{name}: exit {r.returncode}")

        error_case("init-scope-project", "init", "--tools", "claude", "--scope", "project", ".")
        if key(version) >= key("2.0.0"):
            error_case("init-amazon-q", "init", "--tools", "amazon-q", "--scope", "project", ".") \
                if key(version) >= key("2.1.0") else error_case("init-amazon-q", "init", "--tools", "amazon-q", ".")

        # The parity corpus, authored once (1.6.0), judged by this CLI. Its root is named like the older captures.
        corpus = base / "parity-corpus"
        corpus.mkdir()
        run(osp + ["init", "--tools", "none", "."], corpus, home)
        for sub in ("specs", "changes"):
            shutil.copytree(CORPUS / sub, corpus / "openspec" / sub, dirs_exist_ok=True)
        for name, extra in (("validate-parity-corpus.json", []), ("validate-parity-corpus-strict.json", ["--strict"])):
            text = run(osp + ["validate", "--all", "--json", *extra], corpus, home, check=False).stdout
            for form in sorted({str(corpus), str(corpus.resolve())}, key=len, reverse=True):
                text = text.replace(form, "/fixture/parity-corpus")
            write_fixture(out / name, sanitize(text, project, home))

        # Artifact instructions (the regeneration-note prompt's input), by the 1.15.0 recipe.
        demo = base / "instructions-project"
        demo.mkdir()
        run(osp + ["init", "--tools", "none", "."], demo, home)
        run(osp + ["new", "change", "demo"], demo, home)
        (demo / "openspec" / "changes" / "demo" / "proposal.md").write_text(PROPOSAL)
        for artifact in ("design", "specs"):
            text = run(osp + ["instructions", artifact, "--change", "demo", "--json"], demo, home).stdout
            write_fixture(out / "instructions" / f"instructions-{artifact}.json", sanitize(text, demo, home))
        print("  instructions/instructions-{design,specs}.json")

        # 2.1+ records each project it sets up; read it after one project init under a fresh HOME, so the record
        # holds only the demo project (the runs above set up others).
        fresh = base / "home-known"
        fresh.mkdir()
        run(osp + ["init", "--tools", "claude", *(["--scope", "project"] if key(version) >= key("2.1.0") else []),
                   "."], project, fresh)
        known = fresh / ".local" / "share" / "openspec" / "projects.json"
        if known.is_file():
            write_fixture(out / "home-state" / "projects.json", sanitize(known.read_text(), project, fresh))
            print("  home-state/projects.json")
    print(f"{version}: done  (npm dist.integrity {integrity(version)})")


def main():
    versions = sys.argv[1:]
    if not versions:
        sys.exit(__doc__)
    for version in versions:
        capture(version)


if __name__ == "__main__":
    main()
