## MODIFIED Requirements

### Requirement: AI tool detection

The plugin SHALL detect all 28 AI tools supported by the OpenSpec CLI 1.3.x using directory scanning, with type classification (CLI vs IDE_PANEL) and CLI ID mapping. The plugin's `CLI_TOOL_IDS` mapping SHALL match the upstream `value` strings in `@fission-ai/openspec/dist/core/config.js` so that any CLI invocation issued by the plugin uses a registry-recognized tool ID.

#### Scenario: Tool detection
- **WHEN** `AiToolDetectionService.detect()` scans the project root
- **THEN** it SHALL check all 28 tool directories and classify each as CLI or IDE_PANEL

#### Scenario: Junie detected as IDE panel
- **WHEN** the project root contains a `.junie` directory
- **THEN** the plugin SHALL list "Junie" in detected tools, classified as IDE_PANEL, with CLI ID `junie`

#### Scenario: Lingma detected as IDE panel
- **WHEN** the project root contains a `.lingma` directory
- **THEN** the plugin SHALL list "Lingma" in detected tools, classified as IDE_PANEL, with CLI ID `lingma`

#### Scenario: ForgeCode detected as CLI tool
- **WHEN** the project root contains a `.forge` directory (the upstream-emitted directory for ForgeCode)
- **THEN** the plugin SHALL list "ForgeCode" in detected tools, classified as CLI, with CLI ID `forgecode`

#### Scenario: Bob Shell detected as CLI tool
- **WHEN** the project root contains a `.bob` directory
- **THEN** the plugin SHALL list "Bob Shell" in detected tools, classified as CLI, with CLI ID `bob`

#### Scenario: ForgeCode directory key differs from CLI ID
- **WHEN** the plugin maps display name "ForgeCode" to a CLI ID
- **THEN** it SHALL emit `forgecode` (matching upstream `--tools forgecode`), even though the directory key in `TOOL_DIRS` is `.forge`

#### Scenario: Original 24 tools still detected
- **WHEN** any of the original 24 tool directories (`.claude`, `.github`, `.cursor`, `.windsurf`, `.cline`, `.gemini`, `.amazonq`, `.agent`, `.augment`, `.codex`, `.codebuddy`, `.continue`, `.cospec`, `.crush`, `.factory`, `.iflow`, `.kilocode`, `.kiro`, `.opencode`, `.pi`, `.qoder`, `.qwen`, `.roo`, `.trae`) exists in the project root
- **THEN** the plugin SHALL list its corresponding tool with the same display name, type, and CLI ID it used prior to the 1.3.x registry expansion
