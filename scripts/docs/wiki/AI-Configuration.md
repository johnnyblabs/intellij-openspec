# AI Configuration

OpenSpec can generate artifacts using AI providers. This page covers setup and usage.

## Supported Providers

| Provider | Models | API Endpoint |
|----------|--------|-------------|
| **Claude** (Anthropic) | claude-sonnet-4-20250514, claude-haiku-4-20250414, claude-opus-4-20250514 | `https://api.anthropic.com/v1/messages` |
| **OpenAI** | gpt-4o, gpt-4o-mini, gpt-4-turbo | `https://api.openai.com/v1/chat/completions` |
| **None** | — | Clipboard/Editor modes only |

## Setup

### 1. Choose a Provider

**Settings → Tools → OpenSpec → AI Provider**

Select `CLAUDE`, `OPENAI`, or `NONE`.

### 2. Set the Model

Choose from the provider's available models or enter a custom model ID.

### 3. Store the API Key

**Settings → Tools → OpenSpec → Test / Store API Key**

Keys are stored securely using IntelliJ's **PasswordSafe** (`AiCredentialStore`), which uses the OS keychain on macOS and the platform credential store on other systems. Keys are never written to disk in plaintext.

## Delivery Modes

| Mode | Enum | Description | Requires API Key |
|------|------|-------------|-----------------|
| **Clipboard** | `CLIPBOARD` | Copies the full generation prompt to your clipboard | No |
| **Editor Tab** | `EDITOR_TAB` | Opens the prompt in a new scratch editor tab | No |
| **Direct API** | `DIRECT_API` | Calls the AI API and writes the result directly to the artifact file | Yes |

When you run **Generate Artifact**, the plugin asks which delivery mode to use (unless running **Generate All**, which always uses Direct API).

## How Generation Works

1. **ArtifactOrchestrationService** resolves the artifact DAG for the change
2. **ArtifactInstruction** is built with:
   - The artifact template
   - The generation instruction
   - Contents of resolved dependencies
   - Unlocked downstream artifacts
3. A prompt is assembled via `ArtifactInstruction.buildPrompt()`
4. Based on delivery mode:
   - **Clipboard/Editor** → prompt is presented to the user
   - **Direct API** → `DirectApiService` calls the provider API and writes the response

## AI Tool Detection

At startup, `AiToolDetectionService` scans the project root for:

| Directory | Tool |
|-----------|------|
| `.claude/` | Claude Code |
| `.github/copilot/` | GitHub Copilot |
| `.cursor/` | Cursor |
| `.windsurf/` | Windsurf |
| `.cline/` | Cline |

Detected tools are shown in the tool window status bar, helping users know which AI assistants are available in the project.

## Troubleshooting

- **"Generate All" is grayed out** → Ensure an AI provider is selected and an API key is stored
- **API errors** → Check the Console tab for HTTP status codes and error messages
- **Rate limiting** → The plugin does not retry; wait and try again
- **Wrong model** → Verify the model ID matches your API plan's available models

---

**Previous:** [[Menu-and-Actions-Reference]] | **Next:** [[Validation]]
