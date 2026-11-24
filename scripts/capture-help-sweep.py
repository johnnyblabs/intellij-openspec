#!/usr/bin/env python3
"""Capture `openspec <command> --help` for every command the plugin runs, across every supported CLI line.

The plugin passes flags to the CLI it finds on the user's machine, which can be any release from the supported
floor up. These captures are the evidence a flag check compares against: which flags each command accepts, per
release. For the newest published patch of every minor from 1.3, and for every 2.x release, this writes under
src/test/resources/fixtures/cli/<version>/help/:

    <command>.txt        stdout of `openspec <command> --help` (`openspec.txt` for the top-level `openspec --help`)
    <command>-<sub>.txt  the same for each subcommand listed under the command's "Commands:" (`store register`)
    index.json           {"cliVersion", "commands": {"<command>[ <sub>]": {"exit": <code>, "file": "<name>.txt" | null}}}

A command the release doesn't have (`store` before 1.5, for example) is recorded with its exit code and
`"file": null`, so a missing command is never mistaken for a missing flag. Older releases answer an unknown
command's `--help` with the top-level help and exit 0, so a help text counts only when its `Usage:` line names
the command. Runs use an isolated HOME and XDG
directories and packages installed into a scratch prefix (scripts/lib/openspec_capture.py), never npx.

Usage: scripts/capture-help-sweep.py                 (the full sweep)
       scripts/capture-help-sweep.py 2.1.1 1.17.0    (only these releases)
"""
import json
import re
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "scripts" / "lib"))
from openspec_capture import cli, key, newest_patch_per_minor, published_versions, run, write_fixture  # noqa: E402

FIXTURES = REPO / "src/test/resources/fixtures/cli"
# Every command the plugin runs (CliRunner callers), plus the top-level help.
COMMANDS = ["openspec", "init", "update", "validate", "status", "instructions", "archive", "schema", "store",
            "workset", "list", "show", "new", "config", "context", "schemas", "templates", "doctor",
            "workspace", "context-store", "initiative"]


def sweep_versions():
    published = published_versions("1.3.0")
    ones = [v for v in newest_patch_per_minor(published) if key(v)[0] == 1]
    return ones + [v for v in published if key(v)[0] >= 2]


def subcommands(help_text):
    """The names listed under a help text's "Commands:" heading (Commander's layout), without `help`."""
    names, inside = [], False
    for line in help_text.splitlines():
        if line.strip() == "Commands:":
            inside = True
        elif inside and line and not line.startswith(" "):
            inside = False
        elif inside:
            m = re.match(r"^  (\S+)", line)
            if m and m.group(1) != "help" and not m.group(1).startswith("-"):
                names.append(m.group(1).split("|")[0])
    return names


def capture(version):
    out = FIXTURES / version / "help"
    osp = cli(version)
    index = {}
    with tempfile.TemporaryDirectory(prefix="osp-help-") as tmp:
        work, home = Path(tmp) / "work", Path(tmp) / "home"
        work.mkdir()
        home.mkdir()
        def one(words):
            r = run(osp + words + ["--help"], work, home, check=False)
            # Commander prints help to stdout and exits 0. An unknown command either fails, or (older releases) prints
            # the top-level help with exit 0, so a help text counts only when its Usage line names the command.
            usage = next((l for l in r.stdout.splitlines() if l.startswith("Usage:")), "")
            usage = re.sub(r"\|\S+", "", usage)  # `store list|ls`: drop the alias
            known = r.returncode == 0 and usage.startswith(" ".join(["Usage: openspec", *words]).rstrip() + " ")
            name = "-".join(words) or "openspec"
            if known:
                write_fixture(out / f"{name}.txt", r.stdout)
            index[" ".join(words) or "openspec"] = {"exit": r.returncode, "file": f"{name}.txt" if known else None}
            return r.stdout if known else ""

        for command in COMMANDS:
            if command == "openspec":
                one([])
                continue
            for sub in subcommands(one([command])):
                one([command, sub])
    write_fixture(out / "index.json", json.dumps({"cliVersion": version, "commands": index}, indent=2) + "\n")
    missing = [c for c, e in index.items() if e["file"] is None]
    print(f"{version}: {len(index) - len(missing)} help texts" + (f", missing {missing}" if missing else ""))


def main():
    for version in sys.argv[1:] or sweep_versions():
        capture(version)


if __name__ == "__main__":
    main()
