# Design: Java 21 compiler

## Context

**Current State:**
- Build uses Java 17 sourceCompatibility and targetCompatibility (VERSION_17)
- Gradle 8.13 is configured (fully supports Java 21 toolchain)
- IntelliJ Platform 2024.1 is the target (plugin.xml specifies `sinceBuild = "241"`)
- IntelliJ 2024.1 requires Java 21+ runtime — users already must be on Java 21
- Codebase has mixed patterns: some records (ValidationResult.java), some traditional POJOs (ArtifactInfo.java)
- Dependencies: Gson 2.10.1, JUnit 5.10.0, IntelliJ Platform SDK 2024.1

**Why the Mismatch Exists:**
- Project may have started on earlier IntelliJ version
- Conservative approach to compatibility
- Not yet evaluated for Java 21 upgrade

**Constraints:**
- Must maintain compatibility with IntelliJ 2024.1 (no platform upgrade)
- Must not break existing functionality
- Must validate all dependencies work on Java 21
- Gradle wrapper already at 8.13 (no Gradle upgrade needed)

**Stakeholders:**
- Plugin developers: Need Java 21 JDK and updated documentation
- CI/CD maintainers: Must configure Java 21 in build pipelines
- End users: No impact (already running IntelliJ on Java 21)

## Goals / Non-Goals

**Goals:**
1. Align build configuration with runtime platform requirements (IntelliJ 2024.1 = Java 21)
2. Enable Java 21 language features in the codebase
3. Configure Gradle toolchain for consistent builds across developer machines
4. Validate 100% of dependencies are Java 21 compatible
5. Document Java 21 requirement clearly in developer setup guides
6. Incrementally adopt Java 21 features where they improve code quality

**Non-Goals:**
- Complete rewrite of codebase using Java 21 features (incremental adoption only)
- Upgrade IntelliJ Platform SDK version (stays on 2024.1)
- Performance benchmarking (Java 21 adoption is for modernization, not optimization)
- Immediate adoption of virtual threads in plugin code (IDE plugin APIs don't yet support)
- Support for Java 20 or earlier (implicit exclusion via IntelliJ 2024.1 requirement)
- Multi-release JAR creation (single-platform plugin doesn't need it)

## Decisions

### Decision 1: Build Configuration Update Strategy

**Choice:** Update build.gradle.kts with Java 21 source/target compatibility AND explicit toolchain configuration.

**Rationale:**
- IntelliJ 2024.1 mandates Java 21 runtime, so there's no compatibility reason to stay on Java 17
- Explicit toolchain prevents "works on my machine" issues where developers have different JDK versions
- Gradle 8.13 has mature Java 21 toolchain support
- Aligning compiler version with runtime version eliminates confusion

**Alternatives Considered:**

| Alternative | Rejected Because |
|-------------|------------------|
| Keep Java 17 for "safety" | False safety — runtime is already Java 21; creates mismatch |
| Source=17, Target=21 | Invalid configuration; source must be ≤ target |
| No toolchain configuration | Allows accidental builds with wrong JDK; inconsistent across team |
| Upgrade to Java 22+ | Unnecessary; Java 21 is LTS and meets all needs |

**Implementation:**
```kotlin
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

---

### Decision 2: Language Feature Adoption Approach

**Choice:** Incremental, opportunistic adoption guided by "improve clarity, don't refactor for refactoring's sake."

**Rationale:**
- Records already exist in codebase (ValidationResult.java) — extend this pattern
- Pattern matching for switch/instanceof reduces boilerplate in validation inspections
- Sequenced collections (`.getFirst()`, `.getLast()`) make intent clearer than `.get(0)`
- Incremental approach minimizes risk of introducing bugs
- Focus on features that improve **readability**, not just novelty

**Adoption Priorities:**

| Priority | Feature | Target Files | Benefit |
|----------|---------|--------------|---------|
| **High** | Records for data classes | ArtifactInfo.java, ChangeMetadata.java, ArtifactInstruction.java | Reduce boilerplate, immutability by default |
| **Medium** | Pattern matching (instanceof/switch) | DeltaSpecInspection.java, SpecFormatInspection.java, BuiltInValidator.java | Simplify type checking logic |
| **Medium** | Sequenced collections | ChangeService.java (DAG traversal), SpecParsingService.java | Clearer first/last element access |
| **Low** | Sealed classes | ValidationIssue.Severity (if expanded to class hierarchy) | Type-safe exhaustive switches (future) |
| **Future** | Virtual threads readiness | CliRunner.java (async CLI calls) | Prepare for future IntelliJ plugin API improvements |

**Alternatives Considered:**

| Alternative | Rejected Because |
|-------------|------------------|
| Big-bang refactor (all files at once) | High risk; hard to debug if issues arise |
| No adoption of new features | Wastes opportunity to improve codebase maintainability |
| Adopt everything immediately | Virtual threads not yet supported in IntelliJ plugin APIs |

**Guidelines for Developers:**
- Convert classes to records if they: (1) have only getters, (2) no mutable state, (3) no complex logic
- Use pattern matching when replacing `if (x instanceof Type)` chains
- Prefer `.getFirst()` over `.get(0)` when expressing "first element" intent
- Avoid premature sealed classes until type hierarchy stabilizes

---

### Decision 3: Dependency Validation Process

**Choice:** Audit all declared dependencies for Java 21 compatibility; block upgrade if any are incompatible.

**Rationale:**
- Plugin must not ship with dependencies that fail at runtime on Java 21
- Proactive validation prevents surprises after deployment
- All current dependencies are Java 21 compatible (verified below)

**Validation Process:**
1. List all dependencies from build.gradle.kts
2. Check official documentation or changelog for Java 21 support
3. Run full test suite (`./gradlew test`) to catch runtime issues
4. Run plugin in sandbox IDE (`./gradlew runIde`) for integration testing
5. Document results in this design doc

**Dependency Compatibility Matrix:**

| Dependency | Version | Java 21 Support | Verification |
|------------|---------|-----------------|--------------|
| Gson | 2.10.1 | ✅ Yes (Java 8+) | [Gson release notes](https://github.com/google/gson) |
| JUnit 5 | 5.10.0 | ✅ Yes (Java 8+) | [JUnit 5 docs](https://junit.org/junit5/) |
| IntelliJ Platform SDK | 2024.1 | ✅ Yes (requires Java 21) | Built on Java 21 |
| Gradle | 8.13 | ✅ Yes (Java 21 toolchain) | [Gradle compatibility](https://docs.gradle.org/current/userguide/compatibility.html) |

**Risk Mitigation:** If any dependency breaks, either upgrade to a compatible version or replace it.

**Alternatives Considered:**

| Alternative | Rejected Because |
|-------------|------------------|
| Skip validation, assume compatibility | Could cause runtime failures for users |
| Manual testing only (no test suite) | Misses edge cases in automated tests |
| Use outdated dependencies for "safety" | Defeats purpose of Java 21 upgrade |

---

### Decision 4: Migration Strategy (Phased Rollout)

**Choice:** Three-phase migration with validation gates between phases.

**Rationale:**
- Phase 1 (build config) is low-risk and immediately validates the upgrade
- Phase 2 (language adoption) can proceed iteratively file-by-file
- Phase 3 (future features) deferred until IDE platform supports them
- Rollback is straightforward if issues are caught early

**Phase Breakdown:**

#### Phase 1: Build Configuration (Week 1)
**Goal:** Update build files, validate compilation and tests pass.

**Steps:**
1. Update `build.gradle.kts`:
   ```kotlin
   java {
       sourceCompatibility = JavaVersion.VERSION_21
       targetCompatibility = JavaVersion.VERSION_21
       toolchain {
           languageVersion = JavaLanguageVersion.of(21)
       }
   }
   ```
2. Run: `./gradlew clean build --warning-mode=all`
3. Run: `./gradlew test` (all tests must pass)
4. Run: `./gradlew verifyPlugin` (validate plugin.xml)
5. Run: `./gradlew runIde` (manual smoke test in sandbox)

**Validation Gate:** All tests pass, plugin loads in sandbox IDE without errors.

#### Phase 2: Incremental Language Adoption (Weeks 2-4)
**Goal:** Convert appropriate classes to records; add pattern matching where it clarifies code.

**High-Priority Conversions (Records):**
- `ArtifactInfo.java` → record with validation in compact constructor
- `ChangeMetadata.java` → record (already mostly immutable)
- `ArtifactInstruction.java` → record
- `Requirement.java` → record

**Pattern Matching Candidates:**
- `DeltaSpecInspection.java`: Replace instanceof chains in validation logic
- `BuiltInValidator.java`: Type-based validation routing
- `SpecFormatInspection.java`: Markdown node type checks

**Process for Each Conversion:**
1. Convert one file at a time
2. Run: `./gradlew test` (specific test class if available)
3. Manual test in sandbox IDE for that feature
4. Commit with clear message: "refactor: convert ArtifactInfo to record"

**Validation Gate:** No test failures after each conversion; feature behavior unchanged.

#### Phase 3: Future Enhancements (Post-v0.1.0)
**Goal:** Prepare for virtual threads, evaluate sealed classes.

**Deferred Until:**
- IntelliJ plugin API provides virtual thread-compatible async APIs
- Type hierarchies stabilize (e.g., if ValidationIssue.Severity becomes a sealed interface)

---

### Decision 5: Documentation and Communication

**Choice:** Update all developer-facing documentation to reflect Java 21 requirement.

**Rationale:**
- New contributors must know to install Java 21 JDK
- CI/CD configuration must be updated
- Avoids confusion about "why doesn't it build?"

**Documentation Updates:**

| File/Location | Change |
|---------------|--------|
| `scripts/docs/wiki/Build-and-Dev-Setup.md` | Update "Java 17" → "Java 21" in requirements section |
| `README.md` (if exists) | Add "Requires Java 21 JDK for development" |
| `.idea/misc.xml` (if present) | Set project JDK to Java 21 |
| CI/CD config (GitHub Actions, etc.) | Set `java-version: '21'` |
| `CHANGELOG.md` or release notes | Mention Java 21 upgrade in v0.1.0 notes |

**Developer Communication:**
- Slack/email announcement: "Plugin now builds with Java 21"
- Include instructions: "Update your JDK to Java 21+"
- Provide troubleshooting: "If build fails, check `java -version`"

**Alternatives Considered:**

| Alternative | Rejected Because |
|-------------|------------------|
| No documentation updates | Causes onboarding confusion |
| Document "Java 17 or later" | Misleading; we require 21 specifically for toolchain |

---

## Risks / Trade-offs

### Risk 1: Developer Environment Issues
**Risk:** Some developers may not have Java 21 installed; build fails locally.

**Likelihood:** Medium  
**Impact:** Low (fixable)

**Mitigation:**
- Update documentation prominently (README, wiki)
- Gradle toolchain will auto-download Java 21 if configured
- Add troubleshooting section: "If build fails, run `java -version` and install JDK 21"

---

### Risk 2: CI/CD Pipeline Failures
**Risk:** Automated builds fail if CI runners don't have Java 21.

**Likelihood:** High (if not updated)  
**Impact:** High (blocks merges)

**Mitigation:**
- Update CI configuration files **before** merging build.gradle.kts changes
- Test CI pipeline on a branch before merging to main
- Use GitHub Actions `setup-java@v3` with `java-version: '21'`

---

### Risk 3: Record Conversion Introduces Bugs
**Risk:** Converting POJOs to records changes semantics (e.g., equals/hashCode, no setters).

**Likelihood:** Medium  
**Impact:** Medium (could break features)

**Mitigation:**
- Convert only classes with no complex logic
- Run full test suite after each conversion
- Manual test affected features in sandbox IDE
- Use feature flags or rollback if issues found

---

### Risk 4: Hidden Dependency Incompatibilities
**Risk:** A transitive dependency doesn't support Java 21.

**Likelihood:** Low (all direct dependencies verified)  
**Impact:** High (runtime crashes)

**Mitigation:**
- Run `./gradlew dependencies` to inspect transitive deps
- Run plugin in sandbox IDE and exercise all features
- Monitor for ClassLoader errors or NoSuchMethodErrors
- Have rollback plan ready

---

### Risk 5: Pattern Matching Misuse
**Risk:** Developers use pattern matching where simple instanceof is clearer.

**Likelihood:** Low  
**Impact:** Low (code readability)

**Mitigation:**
- Provide clear guidelines in documentation: "Use pattern matching when it reduces boilerplate"
- Code review checklist item: "Is this pattern matching justified?"
- Example: Good use case = switch with multiple types; Bad = single instanceof

---

### Trade-off 1: Incremental vs. Complete Refactor
**Trade-off:** Incremental adoption means mixed codebase (some records, some POJOs).

**Chosen:** Incremental  
**Reason:** Lower risk; allows validation at each step; doesn't block other development.

**Consequence:** Codebase will have inconsistent patterns for a few weeks. Acceptable for safety.

---

### Trade-off 2: Explicit Toolchain vs. Developer JDK
**Trade-off:** Explicit toolchain downloads Java 21 automatically; may surprise developers with large download.

**Chosen:** Explicit toolchain  
**Reason:** Consistency across team > local disk space concerns.

**Consequence:** First build may take longer (download JDK); one-time cost for reliability.

---

## Migration Plan

### Pre-Migration Checklist
- [ ] All developers notified of upcoming Java 21 upgrade
- [ ] CI/CD configuration updated to use Java 21
- [ ] Branch created: `feature/java-21-upgrade`

### Phase 1: Build Configuration (Day 1-2)

**Steps:**
1. **Update build.gradle.kts:**
   ```kotlin
   java {
       sourceCompatibility = JavaVersion.VERSION_21
       targetCompatibility = JavaVersion.VERSION_21
       toolchain {
           languageVersion = JavaLanguageVersion.of(21)
       }
   }
   ```

2. **Validate build:**
   ```bash
   ./gradlew clean
   ./gradlew build --warning-mode=all
   # Expect: BUILD SUCCESSFUL
   ```

3. **Run all tests:**
   ```bash
   ./gradlew test
   # Expect: All tests pass
   ```

4. **Verify plugin descriptor:**
   ```bash
   ./gradlew verifyPlugin
   # Expect: No errors
   ```

5. **Manual smoke test:**
   ```bash
   ./gradlew runIde
   # Test: Open OpenSpec project, browse tree, generate artifact
   ```

6. **Commit:** `build: upgrade to Java 21 compiler`

**Success Criteria:** Build passes, tests pass, plugin loads in IDE.

---

### Phase 2: Language Feature Adoption (Week 1-2)

**Day 3-5: Convert Data Classes to Records**

Priority order (lowest risk first):

1. **ArtifactInfo.java** (simple POJO, no complex logic)
   ```bash
   # Edit file, then:
   ./gradlew test --tests '*ArtifactInfoTest'
   ./gradlew runIde  # Manual test
   git commit -m "refactor: convert ArtifactInfo to record"
   ```

2. **ChangeMetadata.java**
   ```bash
   ./gradlew test --tests '*ChangeMetadataTest'
   git commit -m "refactor: convert ChangeMetadata to record"
   ```

3. **ArtifactInstruction.java**
4. **Requirement.java**

**Day 6-10: Add Pattern Matching**

1. **DeltaSpecInspection.java** (validation logic with instanceof chains)
   - Replace: `if (node instanceof Heading h) { ... }`
   - Test: Run validation inspection on sample delta spec
   - Commit: `refactor: use pattern matching in DeltaSpecInspection`

2. **BuiltInValidator.java**
3. **SpecFormatInspection.java**

**Day 11-14: Sequenced Collections**

1. Audit usage of `.get(0)` and `.get(list.size()-1)`
2. Replace with `.getFirst()` and `.getLast()` where intent is "first/last"
3. Test affected methods
4. Commit: `refactor: use sequenced collections for clarity`

**Success Criteria:** All tests pass after each change; no regressions in functionality.

---

### Phase 3: Documentation and Finalization (Day 15)

**Steps:**
1. **Update documentation:**
   - `scripts/docs/wiki/Build-and-Dev-Setup.md`: Change "Java 17" → "Java 21"
   - Add troubleshooting: "Ensure `java -version` shows 21+"
   - Update IDE configuration docs

2. **Create JAVA21-FEATURES.md:**
   - Guidelines for using records
   - Guidelines for pattern matching
   - Examples from codebase

3. **Update CHANGELOG.md:**
   ```markdown
   ## [0.1.0] - 2026-03-XX
   ### Changed
   - Upgraded to Java 21 compiler and language features
   - Build now requires Java 21 JDK (IntelliJ 2024.1 already requires Java 21 runtime)
   ```

4. **Final validation:**
   ```bash
   ./gradlew clean build test verifyPlugin
   ./gradlew runIde  # Full manual test suite
   ```

5. **Merge to main:** Create PR with summary of all changes

---

### Rollback Strategy

**If critical issue found after merge:**

1. **Immediate rollback (< 1 hour):**
   ```bash
   git revert <commit-hash-of-java-21-upgrade>
   git push origin main
   ```

2. **Revert specific changes (1-4 hours):**
   - If record conversion caused issue: Revert that file only
   - If build config caused issue: Revert build.gradle.kts to Java 17
   - Cherry-pick safe changes back

3. **Deploy previous version (< 30 minutes):**
   - Tag previous stable version
   - Build from that tag
   - Deploy to plugin repository

4. **Document issue:**
   - Create GitHub issue with details
   - Add to TROUBLESHOOTING.md
   - Notify team

---

## Open Questions

### Q1: Should we adopt sealed classes for ValidationIssue.Severity or other enums?
**Status:** Deferred to Phase 3

**Context:** Sealed classes enable exhaustive switch statements without default case.

**Options:**
1. Convert Severity enum to sealed interface with record instances
2. Keep as enum, use pattern matching if needed
3. Wait until more type hierarchies need sealing

**Decision:** Wait. Current enum is sufficient; sealed classes add complexity without clear benefit yet.

---

### Q2: How should we handle virtual threads in CliRunner async operations?
**Status:** Deferred to post-v0.1.0

**Context:** Java 21 supports virtual threads, but IntelliJ plugin APIs don't yet have idiomatic support.

**Options:**
1. Use virtual threads immediately via `Thread.startVirtualThread()`
2. Wait for IntelliJ platform to provide VirtualThread-aware APIs
3. Audit thread-local usage to ensure virtual thread compatibility

**Decision:** Audit and prepare (no thread-locals that would break on virtual threads), but don't actively use virtual threads yet. Revisit when IntelliJ 2025.x provides guidance.

---

### Q3: Do we need to configure JVM flags for Java 21 features?
**Status:** Resolved — No

**Context:** Some Java 21 features (like virtual threads) may have JVM flags.

**Research:** IntelliJ 2024.1 handles JVM configuration. Plugin doesn't need to specify flags.

**Decision:** No action needed. If issues arise, use `./gradlew runIde --debug` to inspect JVM args.

---

### Q4: Should we benchmark performance before/after Java 21 upgrade?
**Status:** Deferred — Not in scope

**Context:** Java 21 may have performance improvements, but plugin is not performance-critical.

**Options:**
1. Run JMH benchmarks on critical paths (e.g., spec parsing)
2. Profile plugin startup time
3. Skip benchmarking

**Decision:** Skip for now. If performance becomes a concern, add benchmarking in future sprint.

---

### Q5: How do we communicate Java 21 requirement to plugin users?
**Status:** Resolved

**Context:** Users must run IntelliJ 2024.1+ on Java 21 runtime.

**Decision:**
- Plugin descriptor already specifies `sinceBuild = "241"` (IntelliJ 2024.1)
- IntelliJ 2024.1 won't run on Java 20 or earlier
- No additional communication needed — platform enforces requirement

**Action:** Mention in README and plugin marketplace description: "Requires IntelliJ IDEA 2024.1+ (Java 21 runtime)"
