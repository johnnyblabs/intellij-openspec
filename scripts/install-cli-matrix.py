#!/usr/bin/env python3
"""Install the OpenSpec CLI releases the `cliMatrix` gate runs against.

`./gradlew cliMatrix` runs every invocation the plugin can make against the real CLI package of each release that
has a captured `--help` sweep (src/test/resources/fixtures/cli/<version>/help/). This script installs those packages
into $OSP_CAPTURE_CACHE (default: <tmp>/osp-capture-pkgs) through scripts/lib/openspec_capture.py, the same cache the
capture scripts use. The Gradle task never installs anything itself.

Usage: scripts/install-cli-matrix.py                (every swept release)
       scripts/install-cli-matrix.py 2.1.1 1.4.1    (only these releases)

Then: OSP_CAPTURE_CACHE=<same dir> ./gradlew cliMatrix
CLI 2.1.0 and later need Node 22.20 or newer; the gate skips those releases on an older Node.
"""
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "scripts" / "lib"))
from openspec_capture import CACHE, key, package  # noqa: E402

FIXTURES = REPO / "src/test/resources/fixtures/cli"


def swept_releases():
    return sorted((d.name for d in FIXTURES.iterdir() if (d / "help" / "index.json").is_file()), key=key)


def main():
    for version in sys.argv[1:] or swept_releases():
        print(f"{version}: {package(version)}")
    print(f"installed into {CACHE}")


if __name__ == "__main__":
    main()
