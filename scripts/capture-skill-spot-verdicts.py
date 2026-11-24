#!/usr/bin/env python3
"""Capture upstream's verdict on each shape a tool folder's skill spot can take, as contract fixtures.

From 2.0 a tool that doesn't read `.agents/skills/` gets, per skill, a link from its own folder into the shared copy,
or a copy where a link can't be made, and git without link support checks a link out as a small text file. Upstream
classifies a spot with `classifySkillLinkSpot` (`dist/core/shared/skill-links.js`): `link`, `copy`, `released`,
`user` or `missing`. Each case below builds one shape on disk under an isolated scratch project, runs upstream's own
classifier on it, and writes:

    src/test/resources/fixtures/cli/<version>/skill-spots/<case>/layout.json   (the detect layout format, links as data)
    src/test/resources/fixtures/cli/<version>/skill-spots/<case>/verdict.txt   upstream's verdict

The spot is always `.claude/skills/openspec-explore`; the shared copy `.agents/skills/openspec-explore/SKILL.md`. The
released copy is the real 1.16.1 skill from `fingerprints/vectors.json`. Every file keeps its full text.

Usage: scripts/capture-skill-spot-verdicts.py            (2.1.1)
       CAPTURE_VERSION=2.0.0 scripts/capture-skill-spot-verdicts.py
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "scripts" / "lib"))
from openspec_capture import package, sanitize, snapshot, write_fixture  # noqa: E402

FIXTURES = REPO / "src/test/resources/fixtures/cli"
SPOT = ".claude/skills/openspec-explore"
SHARED = ".agents/skills/openspec-explore"
SHARED_TEXT = "---\nname: openspec-explore\n---\nThe shared copy.\n"
LINK = "../../.agents/skills/openspec-explore"

CLASSIFY = """
const {pathToFileURL} = await import('node:url');
const m = await import(pathToFileURL(process.argv[2]).href);
process.stdout.write(m.classifySkillLinkSpot(process.argv[3], process.argv[4], process.argv[5]));
"""


def released_text(version):
    cases = json.loads((FIXTURES / version / "fingerprints/vectors.json").read_text())["cases"]
    return next(c["content"] for c in cases if c["name"].endswith("openspec-explore-generated") and c["released"])


def build(case, project, released):
    spot, shared = project / SPOT, project / SHARED
    shared.mkdir(parents=True)
    (shared / "SKILL.md").write_text(SHARED_TEXT)
    spot.parent.mkdir(parents=True)

    def copy(text, *extra):
        spot.mkdir()
        (spot / "SKILL.md").write_text(text)
        for name, body in extra:
            (spot / name).write_text(body)

    if case == "link":
        os.symlink(LINK, spot, target_is_directory=True)
    elif case == "link-target-missing":
        os.symlink(LINK, spot, target_is_directory=True)
        shutil.rmtree(shared)
    elif case == "link-elsewhere":
        (project / "elsewhere").mkdir()
        os.symlink("../../elsewhere", spot, target_is_directory=True)
    elif case == "link-text-slash":
        spot.write_text(LINK)
    elif case == "link-text-slash-newline":
        spot.write_text(LINK + "\n")
    elif case == "link-text-backslash":
        spot.write_text(LINK.replace("/", "\\"))
    elif case == "link-text-elsewhere":
        spot.write_text("../../elsewhere")
    elif case == "copy":
        copy(SHARED_TEXT)
    elif case == "copy-crlf":
        copy(SHARED_TEXT.replace("\n", "\r\n"))
    elif case == "copy-beside-os-junk":
        copy(SHARED_TEXT, (".DS_Store", ""), ("Thumbs.db", ""), ("desktop.ini", ""), ("._SKILL.md", ""))
    elif case == "copy-edited":
        copy(SHARED_TEXT + "My edit.\n")
    elif case == "copy-released":
        copy(released)
    elif case == "copy-beside-notes":
        copy(SHARED_TEXT, ("notes.md", "Mine.\n"))
    elif case == "skill-file-is-link":
        spot.mkdir()
        os.symlink("../../../.agents/skills/openspec-explore/SKILL.md", spot / "SKILL.md")
    elif case == "empty-folder":
        spot.mkdir()
    elif case == "os-junk-only":
        spot.mkdir()
        for name in (".DS_Store", "Thumbs.db", "desktop.ini", "._SKILL.md"):
            (spot / name).write_text("")
    elif case == "absent":
        pass
    else:
        raise ValueError(case)


CASES = ["link", "link-target-missing", "link-elsewhere", "link-text-slash", "link-text-slash-newline",
         "link-text-backslash", "link-text-elsewhere", "copy", "copy-crlf", "copy-beside-os-junk", "copy-edited",
         "copy-released", "copy-beside-notes", "skill-file-is-link", "empty-folder", "os-junk-only", "absent"]


def main():
    version = os.environ.get("CAPTURE_VERSION", "2.1.1")
    module = package(version) / "dist/core/shared/skill-links.js"
    released = released_text(version)
    with tempfile.TemporaryDirectory() as tmp:
        script = Path(tmp) / "classify.mjs"
        script.write_text(CLASSIFY)
        for case in CASES:
            project = Path(tmp) / case
            project.mkdir()
            build(case, project, released)
            verdict = subprocess.run(["node", str(script), str(module), str(project / SPOT), str(project / SHARED),
                                      SHARED_TEXT], check=True, capture_output=True, text=True).stdout.strip()
            files, links = snapshot(project, content=lambda p: p.read_text())
            layout = {"project": files, "home": {}}
            if links:
                layout["links"] = {"project": links}
            out = FIXTURES / version / "skill-spots" / case
            write_fixture(out / "layout.json", sanitize(json.dumps(layout, indent=2, ensure_ascii=False) + "\n", project))
            write_fixture(out / "verdict.txt", verdict + "\n")
            print(f"  skill-spots/{case}: {verdict}")


if __name__ == "__main__":
    main()
