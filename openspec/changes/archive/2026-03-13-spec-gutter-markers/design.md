## Context

The plugin already has `OpenSpecLineMarkerProvider` which adds gutter icons inside spec files for `### Requirement:` headings. This change adds the reverse — gutter icons in Java source files that link back to spec requirements.

IntelliJ's `LineMarkerProvider` API provides `getLineMarkerInfo()` which returns a `LineMarkerInfo` with icon, tooltip, and click handler. The existing provider demonstrates the pattern.

## Goals / Non-Goals

**Goals:**
- Detect `// @spec domain:Requirement Name` comments in Java files
- Show the spec icon in the gutter for matching lines
- Click navigates to `openspec/specs/<domain>/spec.md`
- Tooltip shows the full reference
- Works in any OpenSpec project

**Non-Goals:**
- Auto-generating `@spec` comments from code analysis
- Validating that referenced specs actually exist (future: could be an inspection)
- Supporting non-Java languages (future: register for additional languages)
- Inline code completion for spec references

## Decisions

### Decision 1: Comment-based convention with `@spec`

Use `// @spec <domain>:<requirement-name>` as the linking convention. This is language-agnostic in concept, explicit, and doesn't require runtime dependencies (no annotation library). Developers add these comments where implementation meets specification.

Example:
```java
// @spec plugin-core:Project Detection
public boolean isOpenSpecProject(Project project) {
```

**Alternative considered:** Java annotation `@SpecRef("domain:Requirement")`. Rejected — requires a runtime annotation dependency, only works for Java, and adds noise to compiled code.

### Decision 2: Reuse existing spec icon

Use the same `requirement.svg` icon already used by `OpenSpecLineMarkerProvider` to maintain visual consistency — the gutter icon means "this relates to a spec requirement" in both contexts.

### Decision 3: Navigate to spec file on click

The click handler opens `openspec/specs/<domain>/spec.md` in the editor. No attempt to scroll to the specific requirement heading (that would require parsing the spec file to find the line offset, which is fragile). Opening the file is sufficient since spec files are short.

**Alternative considered:** Navigate to the exact requirement heading. Rejected for v0.2.0 — adds complexity for minimal benefit since spec files are typically under 200 lines.

## Risks / Trade-offs

- **[Risk] PsiElement granularity for comments** → `LineMarkerProvider` receives leaf PsiElements. For Java, `PsiComment` elements contain the full comment text. Match against the text content with a regex.
- **[Risk] Performance with many files open** → `LineMarkerProvider` only runs for visible files in the editor. The regex check is fast (single line, no backtracking). Not a concern.
