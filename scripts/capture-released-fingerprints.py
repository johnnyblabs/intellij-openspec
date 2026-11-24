#!/usr/bin/env python3
"""Capture upstream's released-file fingerprints and test vectors for the plugin's port of the check.

From 2.0 the CLI reads a shared `.agents/skills/` folder's wording only when a skill file is exactly one an OpenSpec
release generated: `isReleasedGeneratedContent` in `dist/core/shared/generated-fingerprint.js`, which hashes a
normalized copy of the file and looks it up in `RELEASED_GENERATED_FINGERPRINTS`. The plugin ships that set as data
and ports the normalization. This script writes both sides of the contract from the published package:

    src/main/resources/openspec/released-skill-fingerprints.txt
        a header naming the source version, then one 16-hex fingerprint per line, sorted
    src/test/resources/fixtures/cli/<version>/fingerprints/vectors.json
        {"version", "set": {"count", "sha256"}, "cases": [{"name", "content", "normalized", "fingerprint", "released"}]}
        (`set.sha256` hashes the sorted fingerprints joined by newlines, with a trailing newline)

The vectors are computed by upstream's own functions. The released ones are real skills from an `init` by the
newest release the set covers ($SOURCE_VERSION, default 1.16.1; 2.1.1's set stops there) and variants of them: a
BOM, CRLF line endings, another `generatedBy` version, a non-version `generatedBy`, and a one-character edit. The
target release's own skill is recorded too: it is newer than the set, so upstream never counts it as released.
Each `init` runs in an isolated scratch project and HOME. Every package is installed into a scratch prefix, never run through `npx`.

Usage: scripts/capture-released-fingerprints.py            (2.1.1)
       CAPTURE_VERSION=2.0.0 scripts/capture-released-fingerprints.py --vectors-only
"""
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "scripts" / "lib"))
from openspec_capture import cli, package, run, write_fixture  # noqa: E402

RESOURCE = REPO / "src/main/resources/openspec/released-skill-fingerprints.txt"
FIXTURES = REPO / "src/test/resources/fixtures/cli"
SKILLS = ["openspec-explore", "openspec-propose"]
# The newest release in the set as of 2.1.1 (its header lists 0.12.0 through 1.16.1).
SOURCE = "1.16.1"

DUMP = """
const {pathToFileURL} = await import('node:url');
const fp = await import(pathToFileURL(process.argv[2]).href);
const set = await import(pathToFileURL(process.argv[3]).href);
const {readFileSync} = await import('node:fs');
const inputs = JSON.parse(readFileSync(process.argv[4], "utf8"));
process.stdout.write(JSON.stringify({
  fingerprints: [...new Set(set.RELEASED_GENERATED_FINGERPRINTS)].sort(),
  cases: inputs.map(c => ({...c, normalized: fp.normalizeGeneratedContent(c.content),
    fingerprint: fp.fingerprintGeneratedContent(c.content), released: fp.isReleasedGeneratedContent(c.content)})),
}));
"""


def variants(skill, text):
    """The real text and the edits a checkout or a user can make to it."""
    out = [(f"{skill}-generated", text),
           (f"{skill}-bom", "﻿" + text),
           (f"{skill}-crlf", text.replace("\n", "\r\n")),
           (f"{skill}-one-char-edit", text.replace("the", "teh", 1))]
    lines = text.split("\n")
    for i, line in enumerate(lines):
        if line.lstrip().startswith("generatedBy:"):
            indent = line[:len(line) - len(line.lstrip())]
            for name, value in (("other-version", '"9.9.9"'), ("bare-version", "1.0.0"),
                                ("single-quoted", "'2.0.0'"), ("not-a-version", '"my build"')):
                edited = lines[:i] + [f"{indent}generatedBy: {value}"] + lines[i + 1:]
                out.append((f"{skill}-generatedBy-{name}", "\n".join(edited)))
            break
    return out


def set_digest(fingerprints):
    """The set's size and the sha256 of its sorted lines, so a test can compare the shipped resource to each release."""
    import hashlib
    return {"count": len(fingerprints),
            "sha256": hashlib.sha256(("\n".join(sorted(fingerprints)) + "\n").encode()).hexdigest()}


def main():
    version = os.environ.get("CAPTURE_VERSION", "2.1.1")
    vectors_only = "--vectors-only" in sys.argv
    pkg = package(version)
    source = os.environ.get("SOURCE_VERSION", SOURCE)
    with tempfile.TemporaryDirectory() as tmp:
        cases = []
        # The released variants: a release inside the set wrote them (its skills sit in the tool's own folder).
        old = Path(tmp) / "old"
        old.mkdir()
        run(cli(source) + ["init", "--tools", "claude"], cwd=old, home=Path(tmp) / "old-home")
        for skill in SKILLS:
            text = (old / ".claude/skills" / skill / "SKILL.md").read_text(encoding="utf-8")
            cases += [{"name": f"{source}-{n}", "content": c} for n, c in variants(skill, text)]
        # The target release's own skill: newer than every release in the set, so never released wording.
        new = Path(tmp) / "new"
        new.mkdir()
        scope = ["--scope", "project"] if tuple(map(int, version.split("."))) >= (2, 1, 0) else []
        run(cli(version) + ["init", "--tools", "claude", *scope], cwd=new, home=Path(tmp) / "new-home")
        cases.append({"name": f"{version}-{SKILLS[0]}-generated",
                      "content": (new / ".agents/skills" / SKILLS[0] / "SKILL.md").read_text(encoding="utf-8")})
        cases.append({"name": "user-written", "content": "---\nname: openspec-explore\n---\nMy own notes.\n"})
        script = Path(tmp) / "dump.mjs"
        script.write_text(DUMP)
        inputs = Path(tmp) / "inputs.json"
        inputs.write_text(json.dumps(cases), encoding="utf-8")
        shared = pkg / "dist/core/shared"
        out = subprocess.run(["node", str(script), str(shared / "generated-fingerprint.js"),
                              str(shared / "released-generated-fingerprints.js"), str(inputs)],
                             capture_output=True, text=True, encoding="utf-8", check=True).stdout
    data = json.loads(out)
    write_fixture(FIXTURES / version / "fingerprints/vectors.json",
                  json.dumps({"version": version, "set": set_digest(data["fingerprints"]), "cases": data["cases"]}, indent=2, ensure_ascii=False) + "\n")
    if not vectors_only:
        header = (f"# Fingerprints of every file an OpenSpec release generated, from CLI {version}'s\n"
                  "# RELEASED_GENERATED_FINGERPRINTS. Generated by scripts/capture-released-fingerprints.py. Do not edit.\n")
        RESOURCE.parent.mkdir(parents=True, exist_ok=True)
        RESOURCE.write_text(header + "\n".join(data["fingerprints"]) + "\n")
        print(f"{len(data['fingerprints'])} fingerprints -> {RESOURCE.relative_to(REPO)}")
    print(f"{len(data['cases'])} vectors -> fixtures/cli/{version}/fingerprints/vectors.json")


if __name__ == "__main__":
    main()
