#!/usr/bin/env python3
"""Capture what upstream tells a user to type to start Explore, per tool and delivery, as a contract fixture.

`openspec init`'s "getting started" hint (core/init.js, startHintLines) decides what a user types for each workflow.
It isn't exported, but its pieces are. So this loads the installed CLI package's dist modules by absolute path with
node and applies the same rules to the canonical `/opsx:explore`:

    tool gets commands  -> getTransformerForTool(id, delivery, resolveCommandSurfaceCapability(id),
                                                 resolveCommandInvocation(id))
    tool gets skills    -> getSkillReferenceTransformer(id)

For every tool with a skills dir and each delivery (both, skills, commands) it records, in
src/test/resources/fixtures/cli/<version>/explore/invocations.json:

    {"cliVersion", "rows": [{"tool", "delivery", "capability", "commands", "skills", "invocation",
                             "commandPath", "skillPath"}]}

`invocation` is null when the tool gets neither; `commandPath`/`skillPath` are null when that file isn't generated.

Usage: scripts/capture-explore-invocations.py   (uses the `openspec` on PATH, or $OPENSPEC_BIN pointing at it)
"""
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

NODE = r"""
const D = process.argv[1];
const {AI_TOOLS} = await import(D + '/core/config.js');
const cs = await import(D + '/core/command-surface.js');
const cr = await import(D + '/utils/command-references.js');
const {CommandAdapterRegistry} = await import(D + '/core/command-generation/index.js');
const rows = [];
for (const t of AI_TOOLS.filter(t => t.skillsDir)) {
  for (const d of ['both', 'skills', 'commands']) {
    const id = t.value;
    const cmd = cs.shouldGenerateCommandsForTool(id, d);
    const sk = cs.shouldGenerateSkillsForTool(id, d);
    let inv = null;
    if (cmd) {
      const tr = cr.getTransformerForTool(id, d, cs.resolveCommandSurfaceCapability(id), cs.resolveCommandInvocation(id));
      inv = tr ? tr('/opsx:explore') : '/opsx:explore';
    } else if (sk) {
      inv = cr.getSkillReferenceTransformer(id)('/opsx:explore');
    }
    const a = CommandAdapterRegistry.get(id);
    rows.push({tool: id, delivery: d, capability: cs.resolveCommandSurfaceCapability(id), commands: cmd, skills: sk,
               invocation: inv, commandPath: cmd && a ? a.getFilePath('explore') : null,
               skillPath: sk ? t.skillsDir + '/skills/openspec-explore/SKILL.md' : null});
  }
}
console.log(JSON.stringify(rows));
"""


def package_dir() -> Path:
    exe = os.environ.get("OPENSPEC_BIN") or shutil.which("openspec")
    if not exe:
        sys.exit("openspec not found on PATH; set OPENSPEC_BIN")
    for parent in Path(exe).resolve().parents:
        if (parent / "package.json").is_file() and (parent / "dist/utils/command-references.js").is_file():
            return parent
    sys.exit(f"no OpenSpec package with dist/ above {exe} (the standalone binary has no JS to load)")


def main():
    pkg = package_dir()
    version = json.loads((pkg / "package.json").read_text())["version"]
    result = subprocess.run(["node", "--input-type=module", "-e", NODE, str(pkg / "dist")],
                            capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"node failed (upstream modules or exports moved?): {result.stderr.strip()}")
    rows = json.loads(result.stdout)
    out = REPO / f"src/test/resources/fixtures/cli/{version}/explore/invocations.json"
    out.write_text(json.dumps({"cliVersion": version, "rows": rows}, indent=1) + "\n")
    print(f"wrote {out}: {len(rows)} rows")


if __name__ == "__main__":
    main()
