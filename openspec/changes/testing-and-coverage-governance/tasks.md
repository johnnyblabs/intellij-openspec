## 1. Establish + record the coverage baseline

- [x] 1.1 Measure current coverage (2026-07-03): INSTRUCTION 32.6%, LINE 30.7%, BRANCH 29.1%, METHOD 37.0%.
- [x] 1.2 Document the baseline inline in `build.gradle.kts` (measured value + date) above the coverage-verification block.

## 2. Set floors just below the baseline

- [x] 2.1 Tighten `jacocoTestCoverageVerification`: INSTRUCTION `0.31`→`0.32`, LINE `0.29`→`0.30`.
- [x] 2.2 Add a BRANCH minimum `0.28` (previously ungoverned; baseline 29.1%).

## 3. ci spec — governance requirements

- [x] 3.1 Apply the `ci` delta: ADD "Test coverage regression floor" (instruction+line+branch; just below recorded baseline; ratchet up, never lower without justification; regression fails the build).
- [x] 3.2 Apply the `ci` delta: ADD "Testable functionality is unit-tested" (platform-independent logic SHALL have real-behavior unit tests added in the same change; external-output parsers contract-tested against captured fixtures).

## 4. Verification

- [x] 4.1 `openspec validate testing-and-coverage-governance --strict` passes.
- [x] 4.2 `./gradlew build` green — current coverage (32.6 / 30.7 / 29.1) clears the tightened floors (0.32 / 0.30 / 0.28).
