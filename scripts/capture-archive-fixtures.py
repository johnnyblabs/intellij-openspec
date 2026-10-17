#!/usr/bin/env python3
"""Capture real `openspec archive` runs as contract fixtures for the plugin's spec merge.

Each case is a small project with one change (`c1`). For every case this records, under
src/test/resources/fixtures/cli/archive-<version>/<case>/:

    <cap>/main-before.md    the main spec before archiving (absent for a new capability)
    <cap>/delta.md          the change's delta spec for that capability
    <cap>/main-after.md     the main spec after a successful archive (absent when refused)
    archive-human.txt       `openspec archive c1 --yes` output
    archive.json            `openspec archive c1 --yes --json` output
    exit.txt                the archive exit code
    validate.json           `openspec validate c1 --json` output
    archive-novalidate.json `--yes --no-validate --json` output, only when validation refused the change
                            (the wording the merge itself would use)

Runs use an isolated HOME and XDG directories, so the real global OpenSpec state is untouched.
Machine paths are replaced with /fixture and the archive date with <DATE>.

Usage: scripts/capture-archive-fixtures.py   (uses the `openspec` on PATH, or $OPENSPEC_BIN)
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import date
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
# The CLI to capture from; e.g. OPENSPEC_BIN="npx -y @fission-ai/openspec@1.14.3" pins a version explicitly.
OPENSPEC = os.environ.get("OPENSPEC_BIN", "openspec").split()

PROPOSAL = """## Why

A probe change used to capture how the archive merges a delta into a main spec.

## What Changes

- Change the demo capability.

## Capabilities

### New Capabilities

### Modified Capabilities
- `demo`: probe.

## Impact

None.
"""

DEMO = """# demo Specification

## Purpose
Demo capability used to capture archive merge behavior in fixtures.

## Requirements

### Requirement: Works
The system SHALL work.

#### Scenario: It works
- **WHEN** used
- **THEN** it works

### Requirement: Other
The system SHALL do other things.

#### Scenario: Other works
- **WHEN** used
- **THEN** other works
"""

def req(name, body="The system SHALL behave.", scenarios=("Behaves",)):
    out = f"### Requirement: {name}\n{body}\n"
    for s in scenarios:
        out += f"\n#### Scenario: {s}\n- **WHEN** used\n- **THEN** it behaves\n"
    return out

FENCE_EXAMPLE = """Example delta:

```markdown
## ADDED Requirements

### Requirement: Phantom
The system SHALL not exist.
```
"""

# case name -> {"main": {cap: content}, "delta": {cap: content}, "change": optional change dir name}
CASES = {
    "modified-ok": {"main": {"demo": DEMO}, "delta": {"demo":
        "## MODIFIED Requirements\n\n" + req("Works", "The system SHALL work reliably.", ("It works",))}},
    "added-ok": {"main": {"demo": DEMO}, "delta": {"demo": "## ADDED Requirements\n\n" + req("New")}},
    "fenced-delta": {"main": {"demo": DEMO}, "delta": {"demo":
        "## ADDED Requirements\n\n### Requirement: Documented\nThe system SHALL document deltas.\n\n"
        + FENCE_EXAMPLE + "\n#### Scenario: Documents\n- **WHEN** used\n- **THEN** it documents\n"}},
    "fenced-main": {"main": {"demo": DEMO.replace(
        "The system SHALL do other things.\n",
        "The system SHALL do other things.\n\n```markdown\n## Notes\n\n### Requirement: Fake\nNot real.\n"
        "#### Scenario: Fake\n```\n")}, "delta": {"demo":
        "## MODIFIED Requirements\n\n" + req("Works", "The system SHALL work reliably.", ("It works",))}},
    "fenced-main-tilde-wrong-closer": {"main": {"demo": DEMO.replace(
        "The system SHALL do other things.\n",
        "The system SHALL do other things.\n\n~~~~\n## Notes\n```\n### Requirement: Fake\n~~~\n~~~~\n")},
        "delta": {"demo": "## ADDED Requirements\n\n" + req("New")}},
    "fenced-main-unclosed": {"main": {"demo": DEMO + "\n```text\n## Trailing\n### Requirement: Fake\n"},
        "delta": {"demo": "## MODIFIED Requirements\n\n"
                  + req("Works", "The system SHALL work reliably.", ("It works",))}},
    "closing-hashes": {"main": {"demo": DEMO.replace("### Requirement: Works\n", "### Requirement: Works ##\n")},
        "delta": {"demo": "## MODIFIED Requirements\n\n"
                  + req("Works", "The system SHALL work reliably.", ("It works",))}},
    "case-added": {"main": {"demo": DEMO}, "delta": {"demo": "## ADDED Requirements\n\n" + req("works")}},
    "spacing-added": {"main": {"demo": DEMO.replace("Requirement: Works", "Requirement: Works well")},
        "delta": {"demo": "## ADDED Requirements\n\n" + req("Works  well")}},
    "case-modified": {"main": {"demo": DEMO}, "delta": {"demo":
        "## MODIFIED Requirements\n\n" + req("works", "The system SHALL work.", ("It works",))}},
    "scenario-drop": {"main": {"demo": DEMO}, "delta": {"demo":
        "## MODIFIED Requirements\n\n" + req("Works", "The system SHALL work.", ("Renamed scenario",))}},
    "modified-missing": {"main": {"demo": DEMO}, "delta": {"demo":
        "## MODIFIED Requirements\n\n" + req("Missing")}},
    "added-exists-different": {"main": {"demo": DEMO}, "delta": {"demo":
        "## ADDED Requirements\n\n" + req("Works", "The system SHALL work differently.", ("It works",))}},
    "added-exists-identical": {"main": {"demo": DEMO}, "delta": {"demo":
        "## ADDED Requirements\n\n### Requirement: Works\nThe system SHALL work.\n\n#### Scenario: It works\n"
        "- **WHEN** used\n- **THEN** it works\n"}},
    "rename-ok": {"main": {"demo": DEMO}, "delta": {"demo":
        "## RENAMED Requirements\n\n- FROM: `### Requirement: Works`\n- TO: `### Requirement: Working`\n"}},
    "rename-target-exists": {"main": {"demo": DEMO}, "delta": {"demo":
        "## RENAMED Requirements\n\n- FROM: `### Requirement: Works`\n- TO: `### Requirement: Other`\n"}},
    "rename-target-case": {"main": {"demo": DEMO}, "delta": {"demo":
        "## RENAMED Requirements\n\n- FROM: `### Requirement: Works`\n- TO: `### Requirement: other`\n"}},
    "rename-source-missing": {"main": {"demo": DEMO}, "delta": {"demo":
        "## RENAMED Requirements\n\n- FROM: `### Requirement: Missing`\n- TO: `### Requirement: Found`\n"}},
    "unpaired-renamed": {"main": {"demo": DEMO}, "delta": {"demo":
        "## RENAMED Requirements\n\n- FROM: `### Requirement: Works`\n"}},
    "bare-renamed": {"main": {"demo": DEMO}, "delta": {"demo":
        "## RENAMED Requirements\n\n- FROM: Works\n- TO: Working\n"}},
    "removed-ok": {"main": {"demo": DEMO}, "delta": {"demo":
        "## REMOVED Requirements\n\n- `### Requirement: Works`\n"}},
    "removed-block-ok": {"main": {"demo": DEMO}, "delta": {"demo":
        "## REMOVED Requirements\n\n### Requirement: Other\n**Reason**: obsolete\n**Migration**: none\n"}},
    "removed-gone": {"main": {"demo": DEMO}, "delta": {"demo":
        "## REMOVED Requirements\n\n- `### Requirement: Missing`\n"}},
    "removed-case": {"main": {"demo": DEMO}, "delta": {"demo":
        "## REMOVED Requirements\n\n- `### Requirement: works`\n"}},
    "duplicate-added": {"main": {"demo": DEMO}, "delta": {"demo":
        "## ADDED Requirements\n\n" + req("New") + "\n" + req("New")}},
    "cross-section": {"main": {"demo": DEMO}, "delta": {"demo":
        "## MODIFIED Requirements\n\n" + req("Works", "The system SHALL work.", ("It works",))
        + "\n## REMOVED Requirements\n\n- `### Requirement: Works`\n"}},
    "mixed-order": {"main": {"demo": DEMO}, "delta": {"demo":
        "## ADDED Requirements\n\n" + req("Added")
        + "\n## MODIFIED Requirements\n\n" + req("Working", "The system SHALL work, renamed.", ("It works",))
        + "\n## RENAMED Requirements\n\n- FROM: `### Requirement: Works`\n- TO: `### Requirement: Working`\n"
        + "\n## REMOVED Requirements\n\n- `### Requirement: Other`\n"}},
    "preserved-text": {"main": {"demo": DEMO.replace(
        "## Requirements\n\n", "## Requirements\n\nRequirements preamble text.\n\n")
        + "\n## Notes\n\nNotes after the requirements.\n"},
        "delta": {"demo": "## ADDED Requirements\n\n" + req("New")}},
    "new-cap": {"main": {}, "delta": {"newcap": "## ADDED Requirements\n\n" + req("Fresh")}},
    "new-cap-purpose": {"main": {}, "delta": {"newcap":
        "## Purpose\nThe new capability handles fresh behavior for every caller.\n\n"
        "## ADDED Requirements\n\n" + req("Fresh")}},
    "two-caps-one-refused": {"main": {"demo": DEMO, "other": DEMO.replace("# demo", "# other")},
        "delta": {"demo": "## ADDED Requirements\n\n" + req("New"),
                  "other": "## MODIFIED Requirements\n\n" + req("Missing")}},
    "dup-modified": {"main": {"demo": DEMO}, "delta": {"demo": "## MODIFIED Requirements\n\n"
        + req("Works", "The system SHALL work.", ("It works",)) + "\n" + req("Works", "The system SHALL work.", ("It works",))}},
    "dup-removed": {"main": {"demo": DEMO}, "delta": {"demo":
        "## REMOVED Requirements\n\n- `### Requirement: Works`\n- `### Requirement: Works`\n"}},
    "dup-rename-from": {"main": {"demo": DEMO}, "delta": {"demo": "## RENAMED Requirements\n\n"
        "- FROM: `### Requirement: Works`\n- TO: `### Requirement: A`\n- FROM: `### Requirement: Works`\n- TO: `### Requirement: B`\n"}},
    "dup-rename-to": {"main": {"demo": DEMO}, "delta": {"demo": "## RENAMED Requirements\n\n"
        "- FROM: `### Requirement: Works`\n- TO: `### Requirement: Same`\n- FROM: `### Requirement: Other`\n- TO: `### Requirement: Same`\n"}},
    "rename-plus-removed": {"main": {"demo": DEMO}, "delta": {"demo":
        "## RENAMED Requirements\n\n- FROM: `### Requirement: Works`\n- TO: `### Requirement: Working`\n"
        "\n## REMOVED Requirements\n\n- `### Requirement: Works`\n"}},
    "rename-plus-removed-case": {"main": {"demo": DEMO}, "delta": {"demo":
        "## RENAMED Requirements\n\n- FROM: `### Requirement: Works`\n- TO: `### Requirement: Working`\n"
        "\n## REMOVED Requirements\n\n- `### Requirement: works`\n"}},
    "modified-old-name-after-rename": {"main": {"demo": DEMO}, "delta": {"demo":
        "## RENAMED Requirements\n\n- FROM: `### Requirement: Works`\n- TO: `### Requirement: Working`\n"
        "\n## MODIFIED Requirements\n\n" + req("Works", "The system SHALL work.", ("It works",))}},
    "rename-to-collides-added": {"main": {"demo": DEMO}, "delta": {"demo":
        "## RENAMED Requirements\n\n- FROM: `### Requirement: Works`\n- TO: `### Requirement: Fresh`\n"
        "\n## ADDED Requirements\n\n" + req("Fresh")}},
    "rename-already-applied": {"main": {"demo": DEMO.replace("Requirement: Works", "Requirement: Working")},
        "delta": {"demo": "## RENAMED Requirements\n\n- FROM: `### Requirement: Works`\n- TO: `### Requirement: Working`\n"}},
    "rename-source-near-miss": {"main": {"demo": DEMO.replace("Requirement: Works", "Requirement: Working")
        .replace("Requirement: Other", "Requirement: works")},
        "delta": {"demo": "## RENAMED Requirements\n\n- FROM: `### Requirement: Works`\n- TO: `### Requirement: Working`\n"}},
    "modified-header-mismatch": {"main": {"demo": DEMO}, "delta": {"demo":
        "## MODIFIED Requirements\n\n### Requirement: Works ##\nThe system SHALL work.\n\n#### Scenario: It works\n"
        "- **WHEN** used\n- **THEN** it works\n"}},
    "new-cap-modified": {"main": {}, "delta": {"newcap": "## MODIFIED Requirements\n\n" + req("Fresh")}},
    "new-cap-renamed": {"main": {}, "delta": {"newcap":
        "## RENAMED Requirements\n\n- FROM: `### Requirement: A`\n- TO: `### Requirement: B`\n"}},
    "new-cap-removed": {"main": {}, "delta": {"newcap": "## ADDED Requirements\n\n" + req("Fresh")
        + "\n## REMOVED Requirements\n\n- `### Requirement: Gone`\n"}},
    "delta-purpose-existing": {"main": {"demo": DEMO}, "delta": {"demo":
        "## Purpose\nA different purpose written in the delta for the demo capability here.\n\n"
        "## ADDED Requirements\n\n" + req("New")}},
    "new-cap-short-purpose": {"main": {}, "delta": {"newcap":
        "## Purpose\nShort purpose.\n\n## ADDED Requirements\n\n" + req("Fresh")}},
    "new-cap-unreadable-purpose": {"main": {}, "delta": {"newcap":
        "## Purpose\nThe purpose text <!-- with a hidden comment --> that runs long enough to count here.\n\n"
        "## ADDED Requirements\n\n" + req("Fresh")}},
    "new-cap-purpose-heading": {"main": {}, "delta": {"newcap":
        "## Purpose\nThe purpose text that runs long enough to count for the threshold here.\n### Not allowed\n\n"
        "## ADDED Requirements\n\n" + req("Fresh")}},
    "new-cap-purpose-h1": {"main": {}, "delta": {"newcap":
        "## Purpose\nThe purpose text that runs long enough to count for the threshold here.\n# Top heading\n\n"
        "## ADDED Requirements\n\n" + req("Fresh")}},
    "new-cap-purpose-unclosed-fence": {"main": {}, "delta": {"newcap":
        "## Purpose\nThe purpose text that runs long enough to count for the threshold here.\n```text\nopen fence\n\n"
        "## ADDED Requirements\n\n" + req("Fresh")}},
    "already-dated-name": {"main": {"demo": DEMO}, "delta": {"demo": "## ADDED Requirements\n\n" + req("New")},
        "change": "2026-01-02-c1"},
    "existing-target": {"main": {"demo": DEMO}, "delta": {"demo": "## ADDED Requirements\n\n" + req("New")},
        "precreate": "TODAY-c1"},
}


def build(project, case):
    change = case.get("change", "c1")
    os_dir = project / "openspec"
    (os_dir / "changes" / change).mkdir(parents=True)
    (os_dir / "config.yaml").write_text("schema: spec-driven\n")
    (os_dir / "changes" / change / ".openspec.yaml").write_text("schema: spec-driven\ncreated: 2026-10-17\n")
    (os_dir / "changes" / change / "proposal.md").write_text(PROPOSAL)
    (os_dir / "changes" / change / "tasks.md").write_text("## 1. Work\n\n- [x] 1.1 Done\n")
    for cap, content in case["main"].items():
        (os_dir / "specs" / cap).mkdir(parents=True)
        (os_dir / "specs" / cap / "spec.md").write_text(content)
    for cap, content in case["delta"].items():
        (os_dir / "changes" / change / "specs" / cap).mkdir(parents=True)
        (os_dir / "changes" / change / "specs" / cap / "spec.md").write_text(content)
    if "precreate" in case:
        name = case["precreate"].replace("TODAY", date.today().isoformat())
        (os_dir / "changes" / "archive" / name).mkdir(parents=True)
    return change


def run(project, env, *args):
    p = subprocess.run([*OPENSPEC, *args], cwd=project, env=env, capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr, p.stdout


def sanitize(text, roots):
    for r in roots:
        text = text.replace(r, "/fixture")
    return text.replace(date.today().isoformat(), "<DATE>")


def capture(name, case, out_root, work):
    out = out_root / name
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    results = {}
    for mode in ("human", "json", "validate", "novalidate"):
        project = work / f"{name}-{mode}"
        env_root = work / f"{name}-{mode}-env"
        for d in ("home", "cfg", "data", "state", "cache"):
            (env_root / d).mkdir(parents=True)
        env = dict(os.environ, HOME=str(env_root / "home"), XDG_CONFIG_HOME=str(env_root / "cfg"),
                   XDG_DATA_HOME=str(env_root / "data"), XDG_STATE_HOME=str(env_root / "state"),
                   XDG_CACHE_HOME=str(env_root / "cache"), OPENSPEC_TELEMETRY="0", NO_COLOR="1", CI="1")
        change = build(project, case)
        roots = sorted({str(project), os.path.realpath(project)}, key=len, reverse=True)
        if mode == "human":
            results[mode] = run(project, env, "archive", change, "--yes") + (project, roots)
        elif mode == "json":
            results[mode] = run(project, env, "archive", change, "--yes", "--json") + (project, roots)
        elif mode == "validate":
            results[mode] = run(project, env, "validate", change, "--json") + (project, roots)
        else:
            results[mode] = run(project, env, "archive", change, "--yes", "--no-validate", "--json") + (project, roots)

    code_h, text_h, _, proj_h, roots_h = results["human"]
    code_j, _, stdout_j, proj_j, roots_j = results["json"]
    _, _, stdout_v, _, roots_v = results["validate"]
    (out / "archive-human.txt").write_text(sanitize(text_h, roots_h))
    (out / "archive.json").write_text(sanitize(stdout_j, roots_j))
    (out / "exit.txt").write_text(f"{code_j}\n")
    (out / "change.txt").write_text(case.get("change", "c1") + "\n")
    (out / "validate.json").write_text(sanitize(stdout_v, roots_v))
    if code_h != code_j:
        sys.exit(f"{name}: human exit {code_h} != json exit {code_j}")
    try:
        validated = json.loads(stdout_v)["items"][0]["valid"]
    except (ValueError, KeyError, IndexError):
        validated = True
    if not validated:
        _, _, stdout_n, _, roots_n = results["novalidate"]
        (out / "archive-novalidate.json").write_text(sanitize(stdout_n, roots_n))

    caps = set(case["main"]) | set(case["delta"])
    for cap in sorted(caps):
        (out / cap).mkdir()
        if cap in case["main"]:
            (out / cap / "main-before.md").write_text(case["main"][cap])
        (out / cap / "delta.md").write_text(case["delta"].get(cap, ""))
        after_h = proj_h / "openspec" / "specs" / cap / "spec.md"
        after_j = proj_j / "openspec" / "specs" / cap / "spec.md"
        if code_j == 0 and after_j.exists():
            if after_h.read_text() != after_j.read_text():
                sys.exit(f"{name}/{cap}: human and json runs wrote different specs")
            (out / cap / "main-after.md").write_text(after_j.read_text())
    # Record where the change went, so the archive-folder rules are captured too.
    archived = sorted(p.name for p in (proj_j / "openspec" / "changes" / "archive").glob("*")) \
        if (proj_j / "openspec" / "changes" / "archive").exists() else []
    legacy = (proj_j / "openspec" / "archive").exists()
    (out / "layout.txt").write_text(sanitize(
        "changes/archive: " + (", ".join(archived) if archived else
                               "(empty)" if (proj_j / "openspec" / "changes" / "archive").exists() else "(absent)") + "\n"
        + f"openspec/archive exists: {legacy}\n", []))
    print(f"{name}: exit {code_j}, valid={validated}, archived={archived}")


def main():
    version = subprocess.run([*OPENSPEC, "--version"], capture_output=True, text=True).stdout.strip()
    out_root = REPO / "src/test/resources/fixtures/cli" / f"archive-{version}"
    out_root.mkdir(parents=True, exist_ok=True)
    (out_root / "version.txt").write_text(version + "\n")
    with tempfile.TemporaryDirectory() as tmp:
        for name, case in CASES.items():
            capture(name, case, out_root, Path(tmp))
    leaks = subprocess.run(["grep", "-rnE", "/Users/|/private/|/var/folders", str(out_root)],
                           capture_output=True, text=True).stdout
    if leaks:
        sys.exit("machine paths left in fixtures:\n" + leaks)


if __name__ == "__main__":
    main()
