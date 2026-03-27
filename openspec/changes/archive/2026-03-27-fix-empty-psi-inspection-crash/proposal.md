## Why

The released v0.2.5 plugin throws `Throwable: Empty PSI elements must not be passed to createDescriptor` at runtime. IntelliJ's `InspectionManager.createProblemDescriptor()` rejects zero-length PSI elements, which `findElementAt()` and `getFirstChild()` can return in Markdown and YAML PSI trees. All three inspection classes are affected.

## What Changes

- Add a `findNonEmptyElement` guard to `DeltaSpecInspection`, `SpecFormatInspection`, and `ConfigValidationInspection` that walks up the PSI tree when a zero-length element is encountered.
- Fix `SpecFormatInspection` where `String.indexOf()` can return `-1`, which is then passed to `findElementAt()`.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `validation`: Inspection classes must guard against empty PSI elements before calling `createProblemDescriptor`.

## Impact

- `DeltaSpecInspection.java` — all `createProblemDescriptor` call sites
- `SpecFormatInspection.java` — all `createProblemDescriptor` call sites plus `indexOf` guard
- `ConfigValidationInspection.java` — all `createProblemDescriptor` call sites