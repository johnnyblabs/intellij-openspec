#!/usr/bin/env python3
"""Capture how the real OpenSpec CLI resolves the OpenSpec root, as contract fixtures for the plugin's resolver.

One scratch "world" holds every layout: a registered store, a project that points at it, a local planning tree that
ignores a pointer, a root in a parent directory, a bare `openspec/`, an unknown and a malformed pointer, a store
whose identity doesn't match its registry entry, and an unhealthy store. For each scenario this runs
`openspec context --json` from the scenario's directory and records stdout, stderr and the exit code.

Output, under src/test/resources/fixtures/cli/<version>/store-root/:

    world.json       every directory and file of the world (relative to <W>), the XDG directories the CLI used,
                     and the store registry and global config exactly as the CLI wrote them
    scenarios.json   [{name, cwd, phase, default_store, context, stderr, exit}]; `phase` names the registry and
                     global config in force for the run (world.json carries one copy of each)

Machine paths are replaced with <W>. The world is built in a fresh temp directory under an isolated HOME and XDG
directories, so the real global OpenSpec state is untouched.

Usage: CAPTURE_VERSION=2.1.1 scripts/capture-store-root.py   (a release installed into scratch, never npx)
       CAPTURE_VERSIONS=1.5.0,1.7.0,1.15.0,2.0.0,2.1.0,2.1.1 scripts/capture-store-root.py
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from openspec_capture import cli, integrity, isolated_env, assert_sanitized  # noqa: E402

DEFAULT_VERSIONS = "1.5.0,1.7.0,1.15.0,2.0.0,2.1.0,2.1.1"

SPEC = """# cap-a Specification

## Purpose
Demo capability held in a store.

## Requirements
### Requirement: Demo behaviour
The system SHALL do the demo thing.

#### Scenario: Demo works
- **WHEN** the demo runs
- **THEN** it succeeds
"""


class World:
    def __init__(self, version):
        self.version = version
        self.argv = cli(version)
        self.tmp = Path(tempfile.mkdtemp(prefix="osp-store-root-"))
        self.w = Path(os.path.realpath(self.tmp))
        self.home = self.w / "home"
        self.home.mkdir()
        env = isolated_env(self.home)
        self.config_dir = Path(env["XDG_CONFIG_HOME"])
        self.data_dir = Path(env["XDG_DATA_HOME"])
        self.scenarios = []
        self.phases = {}

    def rel(self, p):
        return Path(p).relative_to(self.w).as_posix()

    def san(self, text):
        for form in sorted({str(self.tmp), str(self.w)}, key=len, reverse=True):
            text = text.replace(form, "<W>")
        return text

    def run(self, cwd, *args, check=False):
        r = subprocess.run(self.argv + list(args), cwd=cwd, env=isolated_env(self.home), capture_output=True,
                           text=True, stdin=subprocess.DEVNULL)
        if check and r.returncode != 0:
            sys.exit(f"{self.version}: failed: openspec {' '.join(args)}\n{r.stdout}\n{r.stderr}")
        return r

    def write(self, rel, text):
        p = self.w / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text)

    def mkdir(self, rel):
        (self.w / rel).mkdir(parents=True, exist_ok=True)

    @property
    def registry(self):
        return self.data_dir / "openspec" / "stores" / "registry.yaml"

    @property
    def global_config(self):
        return self.config_dir / "openspec" / "config.json"

    def snapshot_phase(self, phase):
        """Record the registry and global config in force for the scenarios of `phase`."""
        read = lambda p: self.san(p.read_text()) if p.is_file() else None  # noqa: E731
        self.phases[phase] = {"registry": read(self.registry), "global_config": read(self.global_config)}

    def context(self, name, cwd, phase, default_store=False):
        r = self.run(self.w / cwd, "context", "--json")
        out = self.san(r.stdout)
        try:
            parsed = json.loads(out)
        except ValueError:
            parsed = {"unparsed": out}
        self.scenarios.append({"name": name, "cwd": cwd, "phase": phase, "default_store": default_store,
                               "context": parsed, "stderr": self.san(r.stderr).strip(), "exit": r.returncode})

    def write_global_config(self, text):
        self.global_config.parent.mkdir(parents=True, exist_ok=True)
        self.global_config.write_text(text)

    def append_registry(self, entries):
        """Hand-written entries the CLI won't register itself: (id, path)."""
        self.registry.write_text(self.registry.read_text().rstrip("\n") + "\n" + "".join(
            f"  {sid}:\n    backend:\n      type: git\n      local_path: {path}\n" for sid, path in entries))

    def build(self):
        # Phase "empty": a pointer with no store registered at all.
        self.write("lonely/openspec/config.yaml", "store: demo\n")
        phase = "empty"
        self.snapshot_phase(phase)
        self.context("pointer-no-stores-registered", "lonely", phase)

        # Phase "stores": one healthy registered store, written by the CLI itself.
        self.run(self.w, "store", "setup", "demo", "--path", str(self.w / "stores" / "demo"), "--no-init-git",
                 "--json", check=True)
        self.write("stores/demo/openspec/specs/cap-a/spec.md", SPEC)

        self.write("app/openspec/config.yaml", "schema: spec-driven\nstore: demo\n")
        self.mkdir("app/src/deep")
        self.write("realapp/openspec/config.yaml", "schema: spec-driven\nstore: demo\n")
        self.mkdir("realapp/openspec/specs")
        self.write("plain/openspec/config.yaml", "schema: spec-driven\n")
        self.mkdir("plain/openspec/changes")
        self.mkdir("plain/sub/deep")
        self.write("ghost/openspec/config.yaml", "store: ghost\n")
        self.write("bad/openspec/config.yaml", "store: [a, b]\n")
        self.mkdir("bare")
        self.mkdir("emptyspec/openspec")
        self.mkdir("noroot")
        self.write("ymlapp/openspec/config.yml", "store: demo\n")
        self.write("emptykey/openspec/config.yaml", "store:\n")
        self.write("badyaml/openspec/config.yaml", "store: [demo\n")
        self.write("badid/openspec/config.yaml", "store: Demo_X\n")

        phase = "stores"
        self.snapshot_phase(phase)
        self.context("declared", "app", phase)
        self.context("declared-from-subdir", "app/src/deep", phase)
        self.context("nearest", "plain", phase)
        self.context("nearest-ancestor", "plain/sub/deep", phase)
        self.context("nearest-ignores-pointer", "realapp", phase)
        self.context("unknown-store", "ghost", phase)
        self.context("malformed-pointer", "bad", phase)
        self.context("no-root-stores-registered", "noroot", phase)
        self.context("empty-openspec-dir", "emptyspec", phase)
        self.context("pointer-in-config-yml", "ymlapp", phase)
        self.context("empty-store-key", "emptykey", phase)
        self.context("unparseable-config", "badyaml", phase)
        self.context("invalid-store-id", "badid", phase)

        # Phase "default": the global default store. 1.5.0 rejects the key, so it is written with --allow-unknown
        # there, to record that the release ignores it.
        r = self.run(self.w, "config", "set", "defaultStore", "demo")
        if r.returncode != 0:
            self.run(self.w, "config", "set", "defaultStore", "demo", "--allow-unknown", check=True)
        phase = "default"
        self.snapshot_phase(phase)
        self.context("global-default", "noroot", phase, default_store=True)
        self.context("global-default-empty-openspec-dir", "emptyspec", phase, default_store=True)
        self.context("global-default-loses-to-nearest", "plain", phase, default_store=True)
        self.context("global-default-loses-to-pointer", "app", phase, default_store=True)
        self.run(self.w, "config", "unset", "defaultStore", check=True)

        # Phase "stale-default": a default naming no registered store. Written by hand, as a removed store leaves it.
        self.write_global_config('{"featureFlags": {}, "defaultStore": "ghost"}\n')
        phase = "stale-default"
        self.snapshot_phase(phase)
        self.context("stale-default-store", "noroot", phase, default_store=True)

        # Phase "empty-default": an empty default counts as unset.
        self.write_global_config('{"featureFlags": {}, "defaultStore": ""}\n')
        phase = "empty-default"
        self.snapshot_phase(phase)
        self.context("empty-default-store", "noroot", phase, default_store=True)
        self.global_config.unlink()

        # Phase "broken": registry entries the CLI must refuse. Written by hand (the CLI won't register them).
        self.write("stores/impostor/.openspec-store/store.yaml", "version: 1\nid: demo\n")
        self.write("stores/impostor/openspec/config.yaml", "schema: spec-driven\n")
        self.mkdir("stores/impostor/openspec/specs")
        self.write("stores/sick/.openspec-store/store.yaml", "version: 1\nid: sick\n")
        for name, metadata in (("garbled", "id: [unclosed\n"), ("noversion", "id: noversion\n"),
                               ("extrakey", "version: 1\nid: extrakey\ncolour: blue\n")):
            self.write(f"stores/{name}/.openspec-store/store.yaml", metadata)
            self.write(f"stores/{name}/openspec/config.yaml", "schema: spec-driven\n")
        self.write("stores/plainfile", "not a directory\n")
        self.append_registry([(sid, self.w / "stores" / d) for sid, d in (
            ("impostor", "impostor"), ("sick", "sick"), ("garbled", "garbled"), ("noversion", "noversion"),
            ("extrakey", "extrakey"), ("filestore", "plainfile"))])
        for sid in ("impostor", "sick", "garbled", "noversion", "extrakey", "filestore"):
            self.write(f"{sid}app/openspec/config.yaml", f"store: {sid}\n")
        phase = "broken"
        self.snapshot_phase(phase)
        self.context("identity-mismatch", "impostorapp", phase)
        self.context("unhealthy-store", "sickapp", phase)
        self.context("unparseable-store-metadata", "garbledapp", phase)
        self.context("store-metadata-without-version", "noversionapp", phase)
        self.context("store-metadata-with-unknown-key", "extrakeyapp", phase)
        self.context("store-root-is-a-file", "filestoreapp", phase)

    def tree(self):
        dirs, files = [], {}
        for dirpath, dirnames, filenames in os.walk(self.w):
            here = Path(dirpath)
            dirnames[:] = sorted(d for d in dirnames if d != ".git" and here / d != self.home)
            for d in dirnames:
                dirs.append(self.rel(here / d))
            for f in sorted(filenames):
                files[self.rel(here / f)] = self.san((here / f).read_text())
        return sorted(dirs), files

    def write_fixtures(self):
        out = REPO / "src/test/resources/fixtures/cli" / self.version / "store-root"
        dirs, files = self.tree()
        cli_version = self.run(self.w, "--version", check=True).stdout.strip()
        world = {"version": self.version, "cli_version": cli_version, "integrity": integrity(self.version), "xdg": {"config": self.rel(self.config_dir), "data": self.rel(self.data_dir)},
                 "dirs": dirs, "files": files, "phases": self.phases}
        for name, data in (("world.json", world), ("scenarios.json", self.scenarios)):
            text = json.dumps(data, indent=2, sort_keys=False) + "\n"
            assert_sanitized(out / name, text)
            out.mkdir(parents=True, exist_ok=True)
            (out / name).write_text(text)
        return out

    def close(self):
        shutil.rmtree(self.tmp, ignore_errors=True)


def main():
    versions = (os.environ.get("CAPTURE_VERSION") or os.environ.get("CAPTURE_VERSIONS") or DEFAULT_VERSIONS)
    for version in versions.split(","):
        world = World(version.strip())
        try:
            world.build()
            out = world.write_fixtures()
            print(f"{world.version}: {len(world.scenarios)} scenarios -> {out.relative_to(REPO)}")
        finally:
            world.close()


if __name__ == "__main__":
    main()
