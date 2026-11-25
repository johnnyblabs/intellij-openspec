#!/usr/bin/env python3
"""Capture the real CLI's verdicts over the 2.x parity corpus, and its apply instructions over wrapped tasks.

For each version named, under src/test/resources/fixtures/cli/<version>/ it writes:

    validate-parity-corpus-2x.json          `validate --all --json` over the committed corpus at
                                            2.1.1/parity-corpus-2x/ (step check, length rules, MODIFIED growth,
                                            delta front matter and their lookalikes, inline fence, unspaced header)
    validate-parity-corpus-2x-strict.json   the same with --strict
    instructions/apply-wrapped-tasks.md     (2.0.0 and later) the tasks.md that capture reads, committed as its input
    instructions/instructions-apply-wrapped.json
                                            (2.0.0 and later) `instructions apply --change wrapped --json` over a
                                            tasks.md with a wrapped task, a hard line break, a task followed by a note
                                            bullet and a plain task

The corpus is copied into a project set up by `init --tools none`, so its root is named /fixture/parity-corpus-2x in
the captures. Every run uses an isolated HOME and XDG directories and a package installed into a scratch prefix
(scripts/lib/openspec_capture.py), never npx. A file is written only after it is checked for machine paths.

Usage: scripts/capture-validator-parity-2x.py 1.14.0 1.14.1 1.14.3 1.15.0 1.16.0 1.16.1 1.17.0 2.0.0 2.1.0 2.1.1
"""
import shutil
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "scripts" / "lib"))
from openspec_capture import cli, key, require_runtime, run, sanitize, write_fixture  # noqa: E402

FIXTURES = REPO / "src/test/resources/fixtures/cli"
CORPUS = FIXTURES / "2.1.1" / "parity-corpus-2x" / "openspec"
PROPOSAL = """## Why

A change whose tasks wrap, used to capture how the CLI reads them.

## What Changes

- Tasks that wrap.
"""
TASKS = """## 1. Tasks

- [ ] 1.1 A task whose text
  wraps onto a second line
- [x] 1.2 A task that ends with a hard break\\
  and continues after it
- [ ] 1.3 A task followed by a note
  - a note bullet, not part of the task
- [ ] 1.4 A plain task
"""


def capture(version):
    require_runtime(version)
    out = FIXTURES / version
    osp = cli(version)
    with tempfile.TemporaryDirectory(prefix="osp-parity2x-") as tmp:
        base = Path(tmp)
        home = base / "home"
        home.mkdir()

        corpus = base / "parity-corpus-2x"
        corpus.mkdir()
        run(osp + ["init", "--tools", "none", "."], corpus, home)
        for sub in ("specs", "changes"):
            shutil.copytree(CORPUS / sub, corpus / "openspec" / sub, dirs_exist_ok=True)
        for name, extra in (("validate-parity-corpus-2x.json", []),
                            ("validate-parity-corpus-2x-strict.json", ["--strict"])):
            text = run(osp + ["validate", "--all", "--json", *extra], corpus, home, check=False).stdout
            for form in sorted({str(corpus), str(corpus.resolve())}, key=len, reverse=True):
                text = text.replace(form, "/fixture/parity-corpus-2x")
            write_fixture(out / name, sanitize(text, None, home))
            print(f"  {version} {name}")

        if key(version) >= key("2.0.0"):
            demo = base / "wrapped-project"
            demo.mkdir()
            run(osp + ["init", "--tools", "none", *(["--scope", "project"] if key(version) >= key("2.1.0") else []),
                       "."], demo, home)
            run(osp + ["new", "change", "wrapped"], demo, home)
            change = demo / "openspec" / "changes" / "wrapped"
            (change / "proposal.md").write_text(PROPOSAL)
            (change / "tasks.md").write_text(TASKS)
            write_fixture(out / "instructions" / "apply-wrapped-tasks.md", TASKS)
            text = run(osp + ["instructions", "apply", "--change", "wrapped", "--json"], demo, home, check=False).stdout
            write_fixture(out / "instructions" / "instructions-apply-wrapped.json", sanitize(text, demo, home))
            print(f"  {version} instructions/instructions-apply-wrapped.json")


def main():
    versions = sys.argv[1:]
    if not versions:
        sys.exit(__doc__)
    for version in versions:
        capture(version)


if __name__ == "__main__":
    main()
