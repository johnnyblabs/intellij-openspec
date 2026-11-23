"""Shared helpers for the OpenSpec CLI fixture-capture scripts.

Every capture runs a published CLI package installed into a scratch npm prefix (never a pinned `npx`, whose cache
path can leak into a fixture, and never the developer's global install), under an isolated HOME and XDG directories
so the real global OpenSpec state is untouched.

    from openspec_capture import package, cli, isolated_env, sanitize, assert_sanitized, require_runtime

Packages are cached under $OSP_CAPTURE_CACHE (default: <tmp>/osp-capture-pkgs) so several scripts share one download.
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

PACKAGE = "@fission-ai/openspec"
CACHE = Path(os.environ.get("OSP_CAPTURE_CACHE") or Path(tempfile.gettempdir()) / "osp-capture-pkgs")

# CLI 2.1.0 and later declare this engine floor; older Node fails at import time with an unhelpful error.
NODE_FLOOR = {(2, 1, 0): (22, 20, 0)}


def key(version):
    return tuple(int(p) for p in version.split("."))


def published_versions(floor="1.3.0"):
    out = subprocess.run(["npm", "view", PACKAGE, "versions", "--json"], check=True,
                         capture_output=True, text=True).stdout
    return sorted((v for v in json.loads(out) if "-" not in v and key(v) >= key(floor)), key=key)


def newest_patch_per_minor(versions):
    """The newest published patch of each MAJOR.MINOR, in version order."""
    best = {}
    for v in versions:
        best[key(v)[:2]] = v
    return [best[m] for m in sorted(best)]


def package(version):
    """The installed package directory for `version`, installing it into the cache on first use."""
    require_runtime(version)
    prefix = CACHE / version
    pkg = prefix / "node_modules" / PACKAGE
    if not (pkg / "package.json").is_file():
        subprocess.run(["npm", "install", "--silent", "--no-audit", "--no-fund", "--prefix", str(prefix),
                        f"{PACKAGE}@{version}"], check=True, capture_output=True)
    installed = json.loads((pkg / "package.json").read_text())["version"]
    if installed != version:
        sys.exit(f"asked for {version} but the cache holds {installed} at {pkg}")
    return pkg


def cli(version):
    """The argv prefix that runs the CLI `version`: node plus the package's own bin file."""
    return ["node", str(package(version) / "bin" / "openspec.js")]


def integrity(version):
    """The npm registry's `dist.integrity` for `version`, recorded as provenance."""
    return subprocess.run(["npm", "view", f"{PACKAGE}@{version}", "dist.integrity"], check=True,
                          capture_output=True, text=True).stdout.strip()


def node_version():
    out = subprocess.run(["node", "--version"], check=True, capture_output=True, text=True).stdout.strip()
    return key(out.lstrip("v"))


def require_runtime(version):
    """Stop with a clear message when this machine can't run `version`: Node too old for 2.1+."""
    for since, floor in NODE_FLOOR.items():
        if key(version) >= since and node_version() < floor:
            sys.exit(f"CLI {version} needs Node {'.'.join(map(str, floor))} or newer; "
                     f"this node is {'.'.join(map(str, node_version()))}")


def require_git_and_network():
    """CLI 2.1.0 installs skills by cloning the upstream repository at its tag, so it needs both."""
    if not shutil.which("git"):
        sys.exit("CLI 2.1.0 installs skills with git, which is not on PATH")
    probe = subprocess.run(["git", "ls-remote", "--exit-code", "https://github.com/Fission-AI/OpenSpec.git", "HEAD"],
                           capture_output=True, text=True, timeout=60)
    if probe.returncode != 0:
        sys.exit("CLI 2.1.0 installs skills from the network, and the upstream repository is unreachable")


def isolated_env(home, **extra):
    """An environment whose HOME and XDG directories are under `home`, with telemetry, colour and prompts off."""
    home = Path(home)
    env = {k: v for k, v in os.environ.items() if not k.startswith("XDG_")}
    env.update(HOME=str(home), OPENSPEC_TELEMETRY="0", DO_NOT_TRACK="1", NO_COLOR="1", FORCE_COLOR="0",
               OPENSPEC_NO_UPDATE_CHECK="1", OPEN_SPEC_INTERACTIVE="0", CI="1",
               npm_config_cache=str(CACHE / "_npm-cache"))
    for var, sub in [("XDG_CONFIG_HOME", ".config"), ("XDG_DATA_HOME", ".local/share"),
                     ("XDG_STATE_HOME", ".local/state"), ("XDG_CACHE_HOME", ".cache")]:
        env[var] = str(home / sub)
    if os.name == "nt":
        env.update(USERPROFILE=str(home), APPDATA=str(home / "AppData" / "Roaming"))
    env.update(extra)
    return env


def run(argv, cwd, home, check=True, **extra_env):
    """Run a CLI command with stdin closed, so a prompt fails fast instead of hanging."""
    result = subprocess.run(argv, cwd=cwd, env=isolated_env(home, **extra_env), capture_output=True, text=True,
                            stdin=subprocess.DEVNULL)
    if check and result.returncode != 0:
        sys.exit(f"failed: {' '.join(map(str, argv))}\n{result.stdout}\n{result.stderr}")
    return result


def sanitize(text, project=None, home=None):
    """Replace machine paths with the fixture convention: the project -> /fixture/demo-project, HOME -> /fixture/home.

    Longest form first: on macOS the resolved path (/private/var/...) contains the unresolved one (/var/...).
    """
    pairs = []
    for real, fake in ((project, "/fixture/demo-project"), (home, "/fixture/home")):
        if real is not None:
            pairs += [(form, fake) for form in {str(real), str(Path(real).resolve())}]
    for form, fake in sorted(pairs, key=lambda p: len(p[0]), reverse=True):
        text = text.replace(form, fake)
    return text


def leaks(text):
    """Machine-specific strings that must never reach a fixture: the capture cache, the temp dir, the real HOME."""
    found = []
    for needle in {str(CACHE), str(CACHE.resolve()), tempfile.gettempdir(), str(Path(tempfile.gettempdir()).resolve()),
                   str(Path.home())}:
        if needle and len(needle) > 4 and needle in text:
            found.append(needle)
    if re.search(r"/private/var/folders/|/var/folders/", text):
        found.append("/var/folders")
    return found


def assert_sanitized(path, text):
    bad = leaks(text)
    if bad:
        sys.exit(f"refusing to write {path}: it still contains machine paths {bad}")


def write_fixture(path, text):
    """Write a fixture after checking it holds no machine path."""
    path = Path(path)
    assert_sanitized(path, text)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def link_target(link, root):
    """A link's target as the fixture records it.

    A relative target that lands inside `root` is kept relative, but recomputed from the real paths. Some installers
    compute it from a differently-resolved prefix (macOS's /private/var against /var), so the target as written can
    climb through the temp directory, and how many `..` segments it has depends on the machine. Anything else is kept
    as written, to be sanitized like any other path.
    """
    written = os.readlink(link)
    real_root = os.path.realpath(root)
    real_target = os.path.realpath(link)
    if not os.path.isabs(written) and (real_target == real_root or real_target.startswith(real_root + os.sep)):
        return os.path.relpath(real_target, os.path.realpath(os.path.dirname(link))).replace(os.sep, "/")
    return written.replace(os.sep, "/")


def snapshot(root, skip=(), content=lambda p: ""):
    """Record a tree without following links: {"files": {path: content}, "links": {path: target}}.

    A link is recorded with its target exactly as written and never descended into, so a skill reached through a
    tool folder's link is recorded once, at its real location.
    """
    root = Path(root)
    files, links = {}, {}
    if not root.exists():
        return files, links

    def skipped(rel):
        return any(rel == s or rel.startswith(s + "/") for s in skip)

    for dirpath, dirnames, filenames in os.walk(root, followlinks=False):
        here = Path(dirpath)
        for name in sorted(dirnames + filenames):
            p = here / name
            rel = p.relative_to(root).as_posix()
            if skipped(rel):
                continue
            if os.path.islink(p):
                links[rel] = link_target(p, root)
            elif p.is_file():
                files[rel] = content(p)
        # os.walk already lists a linked directory in dirnames without descending; prune skipped ones too.
        dirnames[:] = [d for d in dirnames if not os.path.islink(here / d)
                       and not skipped((here / d).relative_to(root).as_posix())]
    return dict(sorted(files.items())), dict(sorted(links.items()))
