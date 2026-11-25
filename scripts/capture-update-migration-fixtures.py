#!/usr/bin/env python3
"""Capture what a real 2.x `openspec update` does to a project an earlier release set up: the 1.x -> 2.x migration.

For every case and every release under test this records, under
src/test/resources/fixtures/cli/<version>/update-migration/<case>/:

    plan.json, plan.exit          `update --dry-run --json`          (the preview's plan)
    plan-force.json               `update --dry-run --force --json`  (what the legacy cleanup flow reads)
    applied.txt, applied.exit     `update`                           (stdout, exit code)
    applied.stderr                its stderr, when non-empty
    before.json, after.json       the project and HOME before the dry runs and after the applied run

The layouts use the detect format (`{"project": {...}, "home": {...}, "links": {...}}`, links as data). File contents
are stored once per release in <version>/update-migration/texts.json, keyed by the first 16 hex digits of their
sha256; a layout value "@<key>" names one (FixtureLayout resolves it). The openspec/ tree is left out: no case changes it.

Each case gets a fresh project and HOME (XDG_* under that HOME, CI=1, OPEN_SPEC_INTERACTIVE=0, stdin closed), is set up
by the seeding releases, and the two dry runs must change nothing (the trees are hashed before and after).
Machine paths become /fixture/demo-project and /fixture/home, and a file still holding a scratch path is refused.

Usage: CAPTURE_VERSIONS=2.0.0,2.1.0,2.1.1 scripts/capture-update-migration-fixtures.py
       (installs every release it needs into the shared scratch prefix: never npx, never the global CLI)

Release 2.1.0 installs skills with Git over the network, so its cases need both; its `no-git` case then runs the
applied update with a PATH holding only node.
"""
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "scripts" / "lib"))
from openspec_capture import (cli, key, require_git_and_network, require_runtime, run, sanitize,  # noqa: E402
                              snapshot, write_fixture)

SEED = "1.17.0"
OUT = REPO / "src/test/resources/fixtures/cli"
HOME_SKIP = (".config", ".local", ".cache", ".npm")
EDIT = "\nA line the user added.\n"


class Skip(Exception):
    """This case doesn't apply to the release under test."""


def seed_init(project, home, tools, version=SEED):
    run(cli(version) + ["init", "--tools", ",".join(tools), "."], project, home)


def append(project, rel, text=EDIT):
    path = project / rel
    with path.open("a") as f:
        f.write(text)


# --- Case setups: (project, home, version under test) -> None ---

def unedited(project, home, v):
    seed_init(project, home, ["claude", "cursor"])


def edited(project, home, v):
    unedited(project, home, v)
    for rel in (".claude/commands/opsx/explore.md", ".claude/skills/openspec-explore/SKILL.md",
                ".cursor/skills/openspec-explore/SKILL.md"):
        append(project, rel)


def notes_beside_skill(project, home, v):
    unedited(project, home, v)
    for tool in (".claude", ".cursor"):
        folder = project / tool / "skills" / "openspec-explore"
        (folder / "notes.md").write_text("# My notes\n\nKept beside the skill.\n")
        (folder / ".DS_Store").write_bytes(b"")


def shared_folder(project, home, v):
    """Codex is rooted at .agents in 1.x, so 1.17 already writes the shared folder and its marker."""
    seed_init(project, home, ["claude", "codex"])
    append(project, ".agents/skills/openspec-explore/SKILL.md")


def nested_commands(project, home, v):
    seed_init(project, home, ["gemini", "claude"])


def narrow_profile(project, home, v):
    """A custom profile with two workflows: 1.17 writes texts the released-file set doesn't hold."""
    run(cli(SEED) + ["config", "set", "profile", "custom"], project, home)
    run(cli(SEED) + ["config", "set", "workflows", '["explore","apply"]'], project, home)
    seed_init(project, home, ["claude"])


def after_200_kept(project, home, v):
    """2.0.0 migrated the edited project and kept the edited copies; the release under test updates it again."""
    if key(v) < key("2.1.0"):
        raise Skip("the case is a later release updating 2.0.0's result")
    edited(project, home, v)
    run(cli("2.0.0") + ["update"], project, home)


def eve_edited(project, home, v):
    """Eve gets a copy, not a link, from 2.1; an edited copy is overwritten in place."""
    if key(v) < key("2.1.1"):
        raise Skip("captured on 2.1.1, which installs offline")
    run(cli(v) + ["init", "--tools", "eve", "--scope", "project", "."], project, home)
    append(project, "agent/skills/openspec-explore/SKILL.md")


def eve_from_210(project, home, v):
    """Eve copies 2.1.0 wrote, refreshed by 2.1.1: unedited copies planned as updates (the allowed-tools negative)."""
    if v != "2.1.1":
        raise Skip("2.1.1 refreshing 2.1.0's copies")
    run(cli("2.1.0") + ["init", "--tools", "eve", "--scope", "project", "."], project, home)


def profile_shrink(project, home, v):
    """A 2.x project whose profile drops workflows: shared skills are deleted, tagged openspec."""
    scope = ["--scope", "project"] if key(v) >= key("2.1.0") else []
    run(cli(v) + ["init", "--tools", "claude,cursor", *scope, "."], project, home)
    run(cli(v) + ["config", "set", "profile", "custom"], project, home)
    run(cli(v) + ["config", "set", "workflows", '["propose","explore"]'], project, home)


def profile_empty(project, home, v):
    """The same with no workflows at all: 2.x also removes the shared-skills marker."""
    scope = ["--scope", "project"] if key(v) >= key("2.1.0") else []
    run(cli(v) + ["init", "--tools", "claude,cursor", *scope, "."], project, home)
    run(cli(v) + ["config", "set", "profile", "custom"], project, home)
    run(cli(v) + ["config", "set", "workflows", "[]"], project, home)


def command_left_in_place(project, home, v):
    """The command folder made read-only: the files can't be removed and are reported as left in place."""
    unedited(project, home, v)
    (project / ".claude" / "commands" / "opsx").chmod(stat.S_IRUSR | stat.S_IXUSR)


def retired_tool_edited(project, home, v):
    """Amazon Q, retired in 2.0: its edited files are kept and reported."""
    seed_init(project, home, ["amazon-q"])
    files = sorted((project / ".amazonq").rglob("*.md"))
    if not files:
        raise Skip("1.17.0 wrote no Amazon Q files")
    append(project, files[0].relative_to(project).as_posix())


def shared_own_skill(project, home, v):
    """A skill of the user's own at an OpenSpec name in .agents/skills: 2.0.0 keeps it as the user's."""
    if v != "2.0.0":
        raise Skip("the kept-as-your-own report is 2.0.0's")
    seed_init(project, home, ["claude"])
    own = project / ".agents" / "skills" / "openspec-explore" / "SKILL.md"
    own.parent.mkdir(parents=True)
    own.write_text("---\nname: openspec-explore\ndescription: My own explore skill\n---\n\nMine.\n")


def read_only_applied(project, home, v):
    """A skill copy made read-only: the applied update fails for a reason other than Git."""
    if v != "2.1.0":
        raise Skip("the negative control for 2.1.0's Git hint")
    unedited(project, home, v)
    (project / ".claude" / "skills" / "openspec-propose").chmod(stat.S_IRUSR | stat.S_IXUSR)


def no_network(project, home, v):
    """2.1.0 with Git but no network (every proxy pointed at a closed port): the clone fails."""
    if v != "2.1.0":
        raise Skip("only 2.1.0 installs from the network")
    unedited(project, home, v)


def no_git(project, home, v):
    if v != "2.1.0":
        raise Skip("only 2.1.0 needs Git")
    unedited(project, home, v)


CASES = {
    "unedited": unedited,
    "edited": edited,
    "notes-beside-skill": notes_beside_skill,
    "shared-folder": shared_folder,
    "nested-commands": nested_commands,
    "narrow-profile": narrow_profile,
    "after-2.0.0-kept": after_200_kept,
    "eve-edited": eve_edited,
    "eve-from-2.1.0": eve_from_210,
    "profile-shrink": profile_shrink,
    "profile-empty": profile_empty,
    "command-left-in-place": command_left_in_place,
    "retired-tool-edited": retired_tool_edited,
    "shared-own-skill": shared_own_skill,
    "read-only-applied": read_only_applied,
    "no-git": no_git,
    "no-network": no_network,
}

# A closed local port for every proxy variable git and node read: the clone fails at once, nothing leaves the machine.
NO_NETWORK = {k: "http://127.0.0.1:9" for k in ("https_proxy", "HTTPS_PROXY", "http_proxy", "HTTP_PROXY", "ALL_PROXY")}
NO_NETWORK["GIT_TERMINAL_PROMPT"] = "0"
# The temporary folder 2.1.0 clones into, named in its failure message.
CLONE_DIR = re.compile(r"(?:/private)?/var/folders/[^ '\n]+?/T/skills-[A-Za-z0-9]+|/tmp/skills-[A-Za-z0-9]+")


def tree_hash(*roots):
    h = hashlib.sha256()
    for root in roots:
        for p in sorted(Path(root).rglob("*")):
            if p.is_symlink():
                h.update(f"L {p} -> {os.readlink(p)}\n".encode())
            elif p.is_file():
                h.update(f"F {p}\n".encode())
                h.update(p.read_bytes())
    return h.hexdigest()


def node_only_path(scratch):
    """A PATH directory holding only node, so `git` can't be found."""
    bin_dir = scratch / "node-only-bin"
    bin_dir.mkdir()
    (bin_dir / "node").symlink_to(shutil.which("node"))
    return str(bin_dir)


class Texts:
    """File contents stored once, keyed by the first 16 hex digits of their sha256."""

    def __init__(self):
        self.by_key = {}

    def ref(self, path, project, home):
        raw = path.read_bytes()
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            text = ""  # OS junk such as .DS_Store: its presence matters, not its bytes
        text = sanitize(text, project, home)
        k = hashlib.sha256(text.encode()).hexdigest()[:16]
        self.by_key[k] = text
        return "@" + k


def layout(project, home, texts):
    content = lambda p: texts.ref(p, project, home)  # noqa: E731
    project_files, project_links = snapshot(project, skip=("openspec", ".git"), content=content)
    home_files, home_links = snapshot(home, skip=HOME_SKIP, content=content)
    result = {"project": project_files, "home": home_files}
    links = {side: l for side, l in (("project", project_links), ("home", home_links)) if l}
    if links:
        result["links"] = links
    return sanitize(json.dumps(result, indent=2, sort_keys=True) + "\n", project, home)


def unlock(root):
    for p in [root, *root.rglob("*")]:
        if not p.is_symlink():
            p.chmod(stat.S_IRWXU if p.is_dir() else stat.S_IRUSR | stat.S_IWUSR)


def capture(v):
    require_runtime(v)
    if v == "2.1.0":
        require_git_and_network()
    out = OUT / v / "update-migration"
    texts = Texts()
    for name, setup in CASES.items():
        scratch = Path(tempfile.mkdtemp(prefix="osp-migration-"))
        project, home = scratch / "demo-project", scratch / "home"
        project.mkdir()
        home.mkdir()
        try:
            try:
                setup(project, home, v)
            except Skip as e:
                print(f"  {v} {name}: skipped ({e})")
                continue
            target = out / name
            before_layout = layout(project, home, texts)
            if name == "after-2.0.0-kept":
                committed = OUT / "2.0.0" / "update-migration" / "edited" / "after.json"
                if committed.exists() and committed.read_text() != before_layout:
                    sys.exit(f"{v} {name}: the seed differs from the committed 2.0.0 edited/after.json")
            write_fixture(target / "before.json", before_layout)
            before = tree_hash(project, home)
            plan = run(cli(v) + ["update", "--dry-run", "--json"], project, home, check=False)
            forced = run(cli(v) + ["update", "--dry-run", "--force", "--json"], project, home, check=False)
            if tree_hash(project, home) != before:
                sys.exit(f"{v} {name}: a dry run changed the project or HOME")
            extra = dict(NO_NETWORK) if name == "no-network" else {}
            if name == "no-git":
                extra = {"PATH": node_only_path(scratch)}
                if shutil.which("git", path=extra["PATH"]):
                    sys.exit("no-git: git is still reachable")
            applied = run(cli(v) + ["update"], project, home, check=False, **extra)
            write_fixture(target / "plan.json", sanitize(plan.stdout, project, home))
            write_fixture(target / "plan.exit", f"{plan.returncode}\n")
            write_fixture(target / "plan-force.json", sanitize(forced.stdout, project, home))
            write_fixture(target / "applied.txt", CLONE_DIR.sub("/tmp/skills-clone", sanitize(applied.stdout, project, home)))
            write_fixture(target / "applied.exit", f"{applied.returncode}\n")
            stderr = CLONE_DIR.sub("/tmp/skills-clone", sanitize(applied.stderr, project, home)).strip()
            if stderr:
                write_fixture(target / "applied.stderr", stderr + "\n")
            write_fixture(target / "after.json", layout(project, home, texts))
            print(f"  {v} {name}: plan exit {plan.returncode}, applied exit {applied.returncode}")
        finally:
            unlock(scratch)
            shutil.rmtree(scratch, ignore_errors=True)
    write_fixture(out / "texts.json", json.dumps(texts.by_key, indent=2, sort_keys=True) + "\n")


def main():
    wanted = os.environ.get("CAPTURE_VERSIONS")
    if not wanted:
        sys.exit("set CAPTURE_VERSIONS, e.g. 2.0.0,2.1.0,2.1.1")
    for v in wanted.split(","):
        capture(v.strip())


if __name__ == "__main__":
    main()
