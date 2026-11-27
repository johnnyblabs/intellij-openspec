#!/usr/bin/env python3
"""Capture real `openspec archive` runs that retire a capability, or refuse to, as the oracle for the plugin's merge.

A change whose `.openspec.yaml` sets `retire_capabilities: true` and whose delta removes a capability's last
requirement makes `openspec archive` (1.8.0 and later) delete the main spec, after checking it for content the merge
can't account for. Each case below is one small project: a main spec `openspec/specs/foo/spec.md` (and, for some, more
files beside it) and a change `c` removing its only requirement. For each release and case this writes, under
`src/test/resources/fixtures/cli/<version>/retire/<case>/`:

    case.json      the inputs: {"spec", "delta", "metadata", "extra": {path: text}} (read back by the contract tests)
    text.out       `archive c --yes` stdout, then stderr, with the archive date replaced by <DATE>
    text.exit      its exit code
    json.out       `archive c --yes --json` stdout (a fresh copy of the project), date replaced
    json.exit      its exit code
    after.json     the tree under openspec/specs after the text run: {path: text}, or {} when it is empty

Every run uses an isolated HOME and XDG directories, `OPENSPEC_TELEMETRY=0`, and a package from the scratch cache
(scripts/lib), never `npx`. The project path is replaced with `/fixture`.

Usage: scripts/capture-spec-retirement.py 1.7.0 1.8.0 1.11.0 1.16.1 2.2.0
"""
import json
import re
import shutil
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "scripts" / "lib"))
from openspec_capture import assert_sanitized, cli, run  # noqa: E402

FIXTURES = REPO / "src/test/resources/fixtures/cli"
MARKED = "schema: spec-driven\ncreated: 2026-11-27\nretire_capabilities: true\n"
UNMARKED = "schema: spec-driven\ncreated: 2026-11-27\n"
DELTA = "## REMOVED Requirements\n\n### Requirement: Foo works\n**Reason**: gone\n**Migration**: none\n"


def spec(statement="The system SHALL foo.", scenario="- **WHEN** x\n- **THEN** y", after=""):
    return ("# foo Specification\n\n## Purpose\nFoo does things.\n\n## Requirements\n\n"
            f"### Requirement: Foo works\n{statement}\n\n#### Scenario: foo\n{scenario}\n{after}")


CASES = {
    "clean": dict(spec=spec(), metadata=MARKED),
    "folder-with-other-file": dict(spec=spec(), metadata=MARKED, extra={"openspec/specs/foo/notes.txt": "keep me\n"}),
    "without-marker": dict(spec=spec(), metadata=UNMARKED),
    "without-marker-with-leftovers": dict(spec=spec() + "\n## Notes\nSomething else.\n", metadata=UNMARKED),
    "extra-section": dict(spec=spec() + "\n## Notes\nSomething else.\n", metadata=MARKED),
    "prose-after-scenario": dict(spec=spec(after="\nA paragraph after the scenario.\n"), metadata=MARKED),
    "args-prose": dict(spec=spec(statement="The system SHALL foo.\n<args> are passed on."), metadata=MARKED),
    "html-block": dict(spec=spec(statement="The system SHALL foo.\n<div>x</div>"), metadata=MARKED),
    "table-row": dict(spec=spec(statement="The system SHALL foo.\n\n| a | b |\n|---|---|\n| 1 | 2 |"), metadata=MARKED),
    "plus-bullets": dict(spec=spec(scenario="+ **WHEN** x\n+ **THEN** y"), metadata=MARKED),
    "indented-continuation": dict(spec=spec(scenario="- **WHEN** x\n  and more of x\n- **THEN** y"), metadata=MARKED),
    "fenced-heading": dict(spec=spec(statement="The system SHALL foo.\n\n```\n## Not a heading\n```"), metadata=MARKED),
    "unhonorable-marker": dict(spec=spec(), metadata="schema: spec-driven\nretire_capabilities: [true\n"),
}
DATE = re.compile(r"\b20\d\d-\d\d-\d\d-c\b")


def layout(project, case):
    (project / "openspec/specs/foo").mkdir(parents=True)
    (project / "openspec/config.yaml").write_text("schema: spec-driven\n")
    (project / "openspec/specs/foo/spec.md").write_text(case["spec"])
    change = project / "openspec/changes/c"
    (change / "specs/foo").mkdir(parents=True)
    (change / ".openspec.yaml").write_text(case["metadata"])
    (change / "proposal.md").write_text("## Why\nDrop foo because it is no longer needed at all here.\n\n"
                                        "## What Changes\n- Remove foo\n")
    (change / "tasks.md").write_text("## 1. Do\n- [x] 1.1 remove\n")
    (change / "specs/foo/spec.md").write_text(DELTA)
    for rel, text in case.get("extra", {}).items():
        (project / rel).write_text(text)


def sanitize(text, project, home):
    for real, fake in sorted({(str(project.resolve()), "/fixture"), (str(project), "/fixture"),
                              (str(home.resolve()), "/fixture/home"), (str(home), "/fixture/home")},
                             key=lambda p: len(p[0]), reverse=True):
        text = text.replace(real, fake)
    return DATE.sub("<DATE>-c", text)


def tree(project):
    root = project / "openspec/specs"
    return {str(p.relative_to(project)).replace("\\", "/"): p.read_text()
            for p in sorted(root.rglob("*")) if p.is_file()} if root.exists() else {}


def capture(version):
    for name, case in CASES.items():
        out = FIXTURES / version / "retire" / name
        out.mkdir(parents=True, exist_ok=True)
        inputs = {"spec": case["spec"], "delta": DELTA, "metadata": case["metadata"], "extra": case.get("extra", {})}
        (out / "case.json").write_text(json.dumps(inputs, indent=2) + "\n")
        for mode, flags in (("text", []), ("json", ["--json"])):
            scratch = Path(tempfile.mkdtemp(prefix="osp-retire-"))
            try:
                project, home = scratch / "project", scratch / "home"
                project.mkdir()
                home.mkdir()
                layout(project, case)
                r = run(cli(version) + ["archive", "c", "--yes"] + flags, project, home, check=False)
                body = r.stdout if mode == "json" else r.stdout + ("\n--- stderr ---\n" + r.stderr if r.stderr else "")
                body = sanitize(body, project, home)
                assert_sanitized(out / f"{mode}.out", body)
                (out / f"{mode}.out").write_text(body)
                (out / f"{mode}.exit").write_text(f"{r.returncode}\n")
                if mode == "text":
                    (out / "after.json").write_text(json.dumps(tree(project), indent=2, sort_keys=True) + "\n")
                    gone = "openspec/specs/foo/spec.md" not in tree(project)
                    print(f"  {version} {name}: exit {r.returncode}{', spec deleted' if gone else ''}")
            finally:
                shutil.rmtree(scratch, ignore_errors=True)


def main():
    versions = sys.argv[1:]
    if not versions:
        sys.exit("name the releases, e.g. 1.7.0 1.8.0 1.11.0 1.16.1 2.2.0")
    for v in versions:
        capture(v)


if __name__ == "__main__":
    main()
