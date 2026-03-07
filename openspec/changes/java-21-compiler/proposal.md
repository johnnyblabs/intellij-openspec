## Why

The project currently uses Java 17 compiler settings (sourceCompatibility and targetCompatibility), but IntelliJ IDEA 2024.1 (the minimum supported version per plugin.xml) requires Java 21 runtime. This mismatch means we're not leveraging the full capabilities of the platform we're targeting. Java 21 LTS brings significant language improvements (pattern matching, record patterns, sequenced collections, virtual threads) that can improve code quality, maintainability, and developer productivity. There are no restrictions preventing the upgrade since our target IDE already requires Java 21.

## What Changes

- Update `build.gradle.kts` to use Java 21 as sourceCompatibility and targetCompatibility
- Configure Gradle toolchain to explicitly use Java 21 for consistent builds across environments
- Validate all dependencies (Gson, JUnit 5, IntelliJ Platform SDK) are compatible with Java 21
- Incrementally adopt Java 21 language features where they improve code clarity (records for data classes, pattern matching in validators, sequenced collections in DAG traversal)
- Update build documentation to reflect Java 21 requirement

## Capabilities

### New Capabilities
<!-- None - this is a build configuration and language modernization change -->

### Modified Capabilities
- `plugin-core`: Build configuration requirements updated to Java 21, enabling modern language features

## Impact

- `build.gradle.kts` — sourceCompatibility and targetCompatibility changed from VERSION_17 to VERSION_21, toolchain configuration added
- `gradle/wrapper/gradle-wrapper.properties` — Already on Gradle 8.13 (supports Java 21 toolchain)
- `src/main/java/**/*.java` — Opportunities to adopt records, pattern matching, sequenced collections
- `src/test/java/**/*.java` — Test code can leverage Java 21 features
- Developer environment — All contributors must have Java 21 JDK installed
- CI/CD pipeline — Build agents must use Java 21
- End users — No impact (IntelliJ 2024.1 already requires Java 21 runtime)
- Plugin deployment — No backward compatibility concerns (target platform already requires Java 21)
