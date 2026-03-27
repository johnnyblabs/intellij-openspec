## 1. Add findNonEmptyElement helper and fix DeltaSpecInspection

- [x] 1.1 Add `findNonEmptyElement(PsiFile, int)` helper to `DeltaSpecInspection` that walks up the PSI tree for a non-empty element
- [x] 1.2 Replace all `findElementAt`/`getFirstChild` + null-check patterns with `findNonEmptyElement` calls in `DeltaSpecInspection`

## 2. Fix SpecFormatInspection

- [x] 2.1 Add `findNonEmptyElement` helper to `SpecFormatInspection`
- [x] 2.2 Guard `String.indexOf()` result against `-1` before passing to `findElementAt`
- [x] 2.3 Replace all `getFirstChild` + null-check patterns with `findNonEmptyElement` calls

## 3. Fix ConfigValidationInspection

- [x] 3.1 Add `findNonEmptyElement` helper to `ConfigValidationInspection`
- [x] 3.2 Replace all `getFirstChild` + null-check patterns with `findNonEmptyElement` calls

## 4. Verify

- [x] 4.1 Run `./gradlew compileJava` to confirm all changes compile
- [x] 4.2 Run existing tests to confirm no regressions
