# Tasks: Java 21 compiler

## 1. Build Configuration

- [x] 1.1 Update build.gradle.kts: sourceCompatibility and targetCompatibility to VERSION_21, add toolchain with languageVersion 21
- [x] 1.2 Run full build and test suite (`./gradlew clean build test verifyPlugin`) — all must pass
- [x] 1.3 Smoke test in sandbox IDE (`./gradlew runIde`) — plugin loads, tree renders, actions work
  - NOTE: Required upgrading target IDE from 2024.1 → 2024.2 (IntelliJ 2024.1 ships JBR 17, not JBR 21; 2024.2 is the first with JBR 21)

## 2. Record Conversions

- [x] 2.1 Convert ArtifactInfo.java to record (immutable data class, no complex logic)
- [x] 2.2 Convert ArtifactInstruction.java to record
- [x] 2.3 ~~Convert Requirement.java to record~~ — SKIPPED: Requirement and Scenario are built incrementally during parsing (setBody, setKeyword, addScenario, addClause); cannot be records
- [x] 2.4 Run full test suite after each conversion — no regressions

## 3. Pattern Matching and Sequenced Collections

- [x] 3.1 ~~Refactor instanceof chains to pattern matching~~ — SKIPPED: no instanceof chains found in these files
- [x] 3.2 Replace `.get(0)` with `.getFirst()` and `.get(list.size()-1)` with `.getLast()` across codebase where intent is first/last element access
- [x] 3.3 Run full test suite — no regressions

## 4. Documentation and Validation

- [x] 4.1 Update README.md to state Java 21 JDK requirement
- [x] 4.2 Run final validation: `./gradlew clean build test` all green
- [x] 4.3 Verify compiled bytecode is Java 21 (major version 65) with `javap -v` on a sample class
