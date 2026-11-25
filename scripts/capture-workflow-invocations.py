#!/usr/bin/env python3
"""Capture what upstream tells a user to type to run a workflow, per tool and delivery, as a contract fixture.

`openspec init`'s "getting started" hint (core/init.js, startHintLines) decides what a user types for each workflow.
It isn't exported, but its pieces are. So this loads the installed CLI package's dist modules by absolute path with
node and applies the same rules to each workflow's canonical `/opsx:<id>` (today `explore`, `update`, `propose` and `ff`):

    tool gets commands  -> getTransformerForTool(id, delivery, resolveCommandSurfaceCapability(id),
                                                 resolveCommandInvocation(id))
    tool gets skills    -> getSkillReferenceTransformer(id)

For every workflow, every tool with a skills dir and each delivery (both, skills, commands) it records, in
src/test/resources/fixtures/cli/<version>/workflow-invocations.json:

    {"cliVersion", "rows": [{"workflow", "skill", "tool", "delivery", "capability", "commands", "skills",
                             "invocation", "commandPath", "skillPath"}]}

`invocation` is null when the tool gets neither; `commandPath`/`skillPath` are null when that file isn't generated.

From CLI 2.0 there are no command files and `delivery` changes nothing, so each tool gets one row per workflow with
`delivery: "skills"`, `commands: false`, `commandPath: null`, the invocation from upstream's own skill-reference
transformer (Codex `$openspec-<skill>`, Kimi `/skill:openspec-<skill>`, prose for tools with no slash surface,
`/openspec-<skill>` elsewhere), `skillPath` where the skill is written (`.agents/skills/...` for shared tools), and
`linkPath`, the tool-folder path that links to it (null when the tool reads `.agents/skills/` itself).

Usage: scripts/capture-workflow-invocations.py   (uses the `openspec` on PATH, or $OPENSPEC_BIN pointing at it)
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
const WORKFLOWS = {explore: 'openspec-explore', update: 'openspec-update-change', propose: 'openspec-propose', ff: 'openspec-ff-change'};
const rows = [];
for (const [w, skill] of Object.entries(WORKFLOWS))
for (const t of AI_TOOLS.filter(t => t.skillsDir)) {
  for (const d of ['both', 'skills', 'commands']) {
    const id = t.value;
    const cmd = cs.shouldGenerateCommandsForTool(id, d);
    const sk = cs.shouldGenerateSkillsForTool(id, d);
    let inv = null;
    if (cmd) {
      const tr = cr.getTransformerForTool(id, d, cs.resolveCommandSurfaceCapability(id), cs.resolveCommandInvocation(id));
      inv = tr ? tr('/opsx:' + w) : '/opsx:' + w;
    } else if (sk) {
      inv = cr.getSkillReferenceTransformer(id)('/opsx:' + w);
    }
    const a = CommandAdapterRegistry.get(id);
    rows.push({workflow: w, skill, tool: id, delivery: d, capability: cs.resolveCommandSurfaceCapability(id), commands: cmd, skills: sk,
               invocation: inv, commandPath: cmd && a ? a.getFilePath(w) : null,
               skillPath: sk ? t.skillsDir + '/skills/' + skill + '/SKILL.md' : null});
  }
}
console.log(JSON.stringify(rows));
"""

# CLI 2.0+: skills only; the paths come from upstream's shared/skill-paths.js.
NODE_2X = r"""
const D = process.argv[1];
const {AI_TOOLS} = await import(D + '/core/config.js');
const cs = await import(D + '/core/command-surface.js');
const cr = await import(D + '/utils/command-references.js');
const sp = await import(D + '/core/shared/skill-paths.js');
const WORKFLOWS = {explore: 'openspec-explore', update: 'openspec-update-change', propose: 'openspec-propose', ff: 'openspec-ff-change'};
const rel = p => p === undefined ? null : p.slice('/P/'.length).split('\\').join('/');
const rows = [];
for (const [w, skill] of Object.entries(WORKFLOWS))
for (const t of AI_TOOLS.filter(t => t.skillsDir)) {
  const id = t.value;
  const skills = rel(sp.resolveToolSkillsDir('/P', t, {homeDir: '/H'}));
  const link = rel(sp.resolveSkillLinkDir('/P', t));
  rows.push({workflow: w, skill, tool: id, delivery: 'skills', capability: cs.resolveCommandSurfaceCapability(id),
             commands: false, skills: true, invocation: cr.getSkillReferenceTransformer(id)('/opsx:' + w),
             commandPath: null, skillPath: skills + '/' + skill + '/SKILL.md',
             linkPath: link === null ? null : link + '/' + skill});
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
    two_x = int(version.split(".")[0]) >= 2
    result = subprocess.run(["node", "--input-type=module", "-e", NODE_2X if two_x else NODE, str(pkg / "dist")],
                            capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"node failed (upstream modules or exports moved?): {result.stderr.strip()}")
    rows = json.loads(result.stdout)
    out = REPO / f"src/test/resources/fixtures/cli/{version}/workflow-invocations.json"
    out.write_text(json.dumps({"cliVersion": version, "rows": rows}, indent=1) + "\n")
    print(f"wrote {out}: {len(rows)} rows")


if __name__ == "__main__":
    main()
