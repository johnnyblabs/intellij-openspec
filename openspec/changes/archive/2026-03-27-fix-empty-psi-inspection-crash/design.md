## Context

The v0.2.5 release introduced three `LocalInspectionTool` subclasses: `DeltaSpecInspection`, `SpecFormatInspection`, and `ConfigValidationInspection`. All three use `PsiFile.findElementAt(offset)` or `PsiFile.getFirstChild()` to locate a PSI element to highlight, then pass it to `InspectionManager.createProblemDescriptor()`. In Markdown and YAML PSI trees, these methods can return zero-length elements (whitespace tokens, empty nodes), which IntelliJ rejects with `Throwable: Empty PSI elements must not be passed to createDescriptor`.

## Goals / Non-Goals

**Goals:**
- Eliminate the runtime crash across all three inspection classes
- Ensure every `createProblemDescriptor` call receives a non-empty PSI element
- Fix the secondary bug in `SpecFormatInspection` where `String.indexOf()` returns `-1`

**Non-Goals:**
- Refactoring inspection logic or changing what they validate
- Extracting a shared base class (each inspection is small and self-contained)

## Decisions

**Add a private static `findNonEmptyElement(PsiFile, int)` helper to each inspection class.** The helper calls `findElementAt(offset)`, falls back to `getFirstChild()`, then walks up via `getParent()` until it finds an element with `getTextLength() > 0`. Returns `null` if no non-empty element exists (callers skip the descriptor in that case).

*Alternative considered*: A shared utility method in a common class. Rejected because the method is 5 lines, each inspection is independently testable, and adding a utility creates coupling for minimal benefit.

**Guard `indexOf` result in `SpecFormatInspection`.** Before passing the result to `findElementAt()`, check for `-1` and `continue` the loop. This prevents an invalid offset from reaching `findElementAt`.

## Risks / Trade-offs

- **[Risk]** Walking up via `getParent()` may highlight a broader element than intended → Acceptable: a slightly wider highlight is far better than a crash. The inspections already fall back to `getFirstChild()` when `findElementAt` returns null, so imprecise highlighting is already an expected behavior.