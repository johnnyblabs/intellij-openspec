#!/usr/bin/env python3
"""Capture the CLI output Explore reads to find a project's specs and change artifacts, as contract fixtures.

This builds one project in a fresh temp dir with an isolated HOME and XDG_* dirs (telemetry off, CI=1):

    openspec/specs/billing/spec.md                 a top-level capability
    openspec/specs/identity/user-auth/spec.md      a nested capability
    changes/add-mfa                                spec-driven, every artifact, a nested delta spec
    changes/research-sso                           a forked schema with an extra `research` artifact
                                                   (research/**/*.md) whose files are nested

and records, under src/test/resources/fixtures/cli/<version>/explore-context/:

    list-specs.json            openspec list --specs --json
    status-add-mfa.json        openspec status --change add-mfa --json
    status-research-sso.json   openspec status --change research-sso --json
    status-missing.json        openspec status --change no-such-change --json (the error envelope)

The project path is replaced by /fixture/project. The real global OpenSpec state is never touched.

Usage: scripts/capture-explore-context-fixtures.py [--basic]   (uses the `openspec` on PATH, or $OPENSPEC_BIN)

--basic seeds only the specs and add-mfa, and records only status-add-mfa.json. It is for CLIs older than 1.4.0,
whose status has no artifactPaths and which can't fork a schema, for example:

    npm_config_cache=/some/tmp OPENSPEC_BIN="npx -y @fission-ai/openspec@1.3.1" \
        scripts/capture-explore-context-fixtures.py --basic
"""
import os
import shlex
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

SPEC = """# {name} Specification

## Purpose
Purpose of {name}, long enough to satisfy the validation rules.

## Requirements

### Requirement: {req}
The system SHALL do {req}.

#### Scenario: Works
- **WHEN** used
- **THEN** it SHALL work
"""

RESEARCH_ARTIFACT = """  - id: research
    generates: "research/**/*.md"
    description: Research notes
    template: design.md
    instruction: |
      Write research notes.
    requires: []

  - id: proposal
"""


def cli() -> list:
    return shlex.split(os.environ.get("OPENSPEC_BIN", "openspec"))


def run(args, cwd, env, check=True):
    result = subprocess.run(cli() + args, cwd=cwd, env=env, capture_output=True, text=True)
    if check and result.returncode != 0:
        sys.exit(f"openspec {' '.join(args)} failed:\n{result.stdout}\n{result.stderr}")
    return result


def isolated_env(home: Path) -> dict:
    env = {k: v for k, v in os.environ.items() if not k.startswith("XDG_")}
    env.update(HOME=str(home), XDG_CONFIG_HOME=str(home / ".config"), XDG_DATA_HOME=str(home / ".local/share"),
               XDG_STATE_HOME=str(home / ".local/state"), XDG_CACHE_HOME=str(home / ".cache"),
               OPENSPEC_TELEMETRY="0", DO_NOT_TRACK="1", CI="1", OPEN_SPEC_INTERACTIVE="0")
    home.mkdir(parents=True, exist_ok=True)
    return env


def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def seed(project: Path, env: dict, basic: bool):
    run(["init", "--tools", "none", "."] if basic else ["init", "--tools", "none", "--no-animation", "."], project, env)
    write(project / "openspec/specs/billing/spec.md", SPEC.format(name="billing", req="Invoice"))
    write(project / "openspec/specs/identity/user-auth/spec.md", SPEC.format(name="identity/user-auth", req="Login"))

    run(["new", "change", "add-mfa"], project, env)
    mfa = project / "openspec/changes/add-mfa"
    write(mfa / "proposal.md", "## Why\nMFA is needed for the identity area of this project.\n\n## What Changes\n"
          "- Add MFA\n\n## Capabilities\n### Modified Capabilities\n- `identity/user-auth`\n\n## Impact\nAuth.\n")
    write(mfa / "design.md", "## Context\nContext.\n\n## Decisions\nDecisions.\n")
    write(mfa / "specs/identity/user-auth/spec.md", "## ADDED Requirements\n\n### Requirement: MFA\n"
          "The system SHALL require MFA.\n\n#### Scenario: Prompted\n- **WHEN** a user logs in\n"
          "- **THEN** MFA SHALL be asked\n")
    write(mfa / "tasks.md", "## 1. Do\n- [ ] 1.1 Add MFA\n")
    if basic:
        return

    run(["schema", "fork", "spec-driven", "research-first"], project, env)
    schema = project / "openspec/schemas/research-first/schema.yaml"
    schema.write_text(schema.read_text().replace("  - id: proposal\n", RESEARCH_ARTIFACT, 1))
    run(["new", "change", "research-sso", "--schema", "research-first"], project, env)
    sso = project / "openspec/changes/research-sso"
    write(sso / "research/summary.md", "# Summary\nNotes.\n")
    write(sso / "research/vendors/okta.md", "# Okta\nNotes.\n")
    write(sso / "proposal.md", "## Why\nResearch SSO options for the identity area of this project.\n\n"
          "## What Changes\n- SSO\n\n## Capabilities\n### New Capabilities\n- `identity/sso`\n\n## Impact\nAuth.\n")


def main():
    basic = "--basic" in sys.argv[1:]
    version = run(["--version"], REPO, os.environ.copy()).stdout.strip()
    out = REPO / f"src/test/resources/fixtures/cli/{version}/explore-context"
    out.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        project = tmp / "project"
        project.mkdir()
        env = isolated_env(tmp / "home")
        seed(project, env, basic)
        captures = {
            "list-specs.json": ["list", "--specs", "--json"],
            "status-add-mfa.json": ["status", "--change", "add-mfa", "--json"],
            "status-research-sso.json": ["status", "--change", "research-sso", "--json"],
            "status-missing.json": ["status", "--change", "no-such-change", "--json"],
        }
        if basic:
            captures = {"status-add-mfa.json": captures["status-add-mfa.json"]}
        for name, args in captures.items():
            result = run(args, project, env, check=name != "status-missing.json")
            text = result.stdout if result.stdout.strip() else result.stderr
            for path in {str(project.resolve()), str(project)}:
                text = text.replace(path, "/fixture/project")
            (out / name).write_text(text)
            print(f"wrote {out / name}")


if __name__ == "__main__":
    main()
