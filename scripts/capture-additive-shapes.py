#!/usr/bin/env python3
"""Capture the additive-shapes set on more releases: `show`, `status` and `instructions` over one committed project.

The project is `src/test/resources/fixtures/cli/1.14.3/additive-shapes/openspec/`: one change, `c`, whose
`.openspec.yaml` carries unknown keys (so `warnings` is populated) and whose spec delta adds one requirement with one
scenario. For each release this copies that tree into a scratch project and writes, under
`src/test/resources/fixtures/cli/<version>/additive-shapes/`:

    show-change.json          `show c --type change --json`
    status.json               `status --change c --json`
    instructions-proposal.json `instructions proposal --change c --json`
    instructions-apply.json   `instructions apply --change c --json`

A command the release lacks (non-zero exit) is skipped and reported, not written. The project path is replaced with
`/fixture`, as in the committed 1.14.3 set, and HOME with `/fixture/home`. Every run uses an isolated HOME and XDG
directories, `OPENSPEC_TELEMETRY=0`, and a package installed into the scratch cache (scripts/lib), never `npx`.

Usage: scripts/capture-additive-shapes.py 1.14.3 2.0.0 2.1.1 2.2.0
       (1.14.3 reproduces the committed set: the script checks it is byte-identical and writes nothing.)
"""
import shutil
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "scripts" / "lib"))
from openspec_capture import cli, assert_sanitized, run  # noqa: E402

FIXTURES = REPO / "src/test/resources/fixtures/cli"
CORPUS = FIXTURES / "1.14.3/additive-shapes/openspec"
COMMANDS = {
    "show-change.json": ["show", "c", "--type", "change", "--json"],
    "status.json": ["status", "--change", "c", "--json"],
    "instructions-proposal.json": ["instructions", "proposal", "--change", "c", "--json"],
    "instructions-apply.json": ["instructions", "apply", "--change", "c", "--json"],
}


def sanitize(text, project, home):
    pairs = []
    for real, fake in ((project, "/fixture"), (home, "/fixture/home")):
        pairs += [(form, fake) for form in {str(real), str(Path(real).resolve())}]
    for form, fake in sorted(pairs, key=lambda p: len(p[0]), reverse=True):
        text = text.replace(form, fake)
    return text


def capture(version):
    out = FIXTURES / version / "additive-shapes"
    scratch = Path(tempfile.mkdtemp(prefix="osp-additive-"))
    try:
        project, home = scratch / "project", scratch / "home"
        home.mkdir()
        shutil.copytree(CORPUS, project / "openspec")
        results = {}
        for name, argv in COMMANDS.items():
            r = run(cli(version) + argv, project, home, check=False)
            if r.returncode != 0:
                print(f"  {version} {name}: exit {r.returncode}, skipped ({(r.stderr or r.stdout).strip()[:120]})")
                continue
            results[name] = sanitize(r.stdout, project, home)
        if version == "1.14.3":
            for name, text in results.items():
                committed = (out / name).read_text()
                if committed != text:
                    sys.exit(f"1.14.3 {name}: differs from the committed capture; the script does not reproduce it")
            print(f"  1.14.3: reproduces the committed set byte for byte ({', '.join(results)})")
            return
        out.mkdir(parents=True, exist_ok=True)
        for name, text in results.items():
            path = out / name
            assert_sanitized(path, text)
            path.write_text(text)
            print(f"  {version} {name}")
    finally:
        shutil.rmtree(scratch, ignore_errors=True)


def main():
    versions = sys.argv[1:]
    if not versions:
        sys.exit("name the releases, e.g. 1.14.3 2.0.0 2.1.1 2.2.0")
    for v in versions:
        capture(v)


if __name__ == "__main__":
    main()
