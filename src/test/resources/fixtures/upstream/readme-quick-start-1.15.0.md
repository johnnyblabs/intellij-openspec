<!-- Captured from Fission-AI/OpenSpec README.md at tag v1.15.0 (commit 8e0288df12d4), ## Quick Start section only.
     Re-capture on every CLI bump:
     gh api 'repos/Fission-AI/OpenSpec/contents/README.md?ref=<tag>' --jq .content | base64 -d | awk '/^## Quick Start/{f=1;print;next} /^## /{f=0} f' -->
## Quick Start

Install OpenSpec. It is a single file and needs no Node.js (on Alpine Linux, first run `apk add libstdc++ libgcc`).

macOS and Linux:

```bash
curl -fsSL https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.sh | sh
```

Windows (PowerShell):

```powershell
irm https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.ps1 | iex
```

Prefer a package manager? `npm install -g @fission-ai/openspec@latest` (needs Node.js 20.19.0 or higher) or the official [Homebrew formula](https://formulae.brew.sh/formula/openspec), `brew install openspec`.

Then navigate to your project directory and initialize:

```bash
cd your-project
openspec init
```

> **Want your AI to do it?** Paste the [setup prompt](https://openspec.dev/docs/installation#install-with-your-ai-assistant) into your coding assistant — it installs the CLI, runs `openspec init`, and verifies the result.

Now talk to your AI:

- **Not sure what to build yet?** Start with `/opsx:explore`, a no-stakes thinking partner that reads your code, weighs options, and shapes a plan before any code gets written. ([Quickstart](https://openspec.dev/docs/quickstart))
- **Already know what you want?** Go straight to `/opsx:propose <what-you-want-to-build>`.

Both are in the default profile. If you want the expanded workflow (`/opsx:new`, `/opsx:continue`, `/opsx:ff`, `/opsx:verify`, `/opsx:bulk-archive`, `/opsx:onboard`), select it with `openspec config profile` and apply with `openspec update`.

`/opsx:propose` is the canonical name; your tool may spell it `/opsx-propose` (Cursor, GitHub Copilot), `@opsx-propose` (Amazon Q) or `$openspec-propose` (Codex). `openspec init` prints the right form for the tools you picked — see [Supported Tools](https://openspec.dev/docs/supported-tools).

> [!NOTE]
> Not sure if your tool is supported? [View the full list](https://openspec.dev/docs/supported-tools) – we support 30+ tools and growing.
>
> Also works with npm, Homebrew, pnpm, yarn, bun, and Nix. [See installation options](https://openspec.dev/docs/installation).

