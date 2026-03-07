## ADDED Requirements

### Requirement: Java 21 Build Configuration

The plugin SHALL be built with Java 21 language level (sourceCompatibility and targetCompatibility set to VERSION_21) and SHALL use an explicit Gradle toolchain configured for Java 21.

#### Scenario: Source compatibility set to Java 21
- **WHEN** the build.gradle.kts is evaluated
- **THEN** the sourceCompatibility SHALL be set to JavaVersion.VERSION_21

#### Scenario: Target compatibility set to Java 21
- **WHEN** the build.gradle.kts is evaluated
- **THEN** the targetCompatibility SHALL be set to JavaVersion.VERSION_21

#### Scenario: Gradle toolchain configured for Java 21
- **WHEN** the build.gradle.kts is evaluated
- **THEN** the Java toolchain languageVersion SHALL be set to JavaLanguageVersion.of(21)

#### Scenario: Build produces Java 21 bytecode
- **WHEN** the plugin is compiled via ./gradlew build
- **THEN** the resulting class files SHALL be Java 21 bytecode (major version 65)

#### Scenario: Build requires Java 21 JDK
- **WHEN** a developer runs ./gradlew build without Java 21 installed
- **THEN** Gradle toolchain SHALL either auto-download Java 21 or report a clear error indicating Java 21 JDK is required

### Requirement: Java 21 Language Feature Support

The codebase SHALL leverage Java 21 language features where they improve code clarity, reduce boilerplate, or enhance type safety.

#### Scenario: Records used for immutable data classes
- **WHEN** a class represents immutable data with only getters and no complex logic
- **THEN** the class SHOULD be implemented as a Java record

#### Scenario: Pattern matching used for type checks
- **WHEN** code performs type-based branching with instanceof checks
- **THEN** developers SHOULD use pattern matching for instanceof or switch expressions (JEP 441) instead of traditional if-else instanceof chains

#### Scenario: Sequenced collections used for ordered access
- **WHEN** code accesses the first or last element of a list
- **THEN** developers SHOULD use .getFirst() or .getLast() methods (JEP 431) instead of .get(0) or .get(list.size()-1)

#### Scenario: Virtual thread compatibility maintained
- **WHEN** code performs I/O operations or creates threads
- **THEN** the code SHALL NOT use ThreadLocal variables in ways that would prevent efficient virtual thread usage

### Requirement: Dependency Java 21 Compatibility

All plugin dependencies (direct and transitive) SHALL be compatible with Java 21 runtime.

#### Scenario: Direct dependencies support Java 21
- **WHEN** a dependency is declared in build.gradle.kts
- **THEN** the dependency MUST be verified to support Java 21 runtime

#### Scenario: Gson dependency compatibility
- **WHEN** the plugin uses Gson 2.10.1 or later
- **THEN** Gson SHALL function correctly on Java 21 (Gson supports Java 8+)

#### Scenario: JUnit 5 dependency compatibility
- **WHEN** the plugin uses JUnit 5.10.0 or later for testing
- **THEN** JUnit 5 SHALL execute tests correctly on Java 21 (JUnit 5 supports Java 8+)

#### Scenario: IntelliJ Platform SDK compatibility
- **WHEN** the plugin targets IntelliJ Platform 2024.1
- **THEN** the platform SDK SHALL require Java 21 runtime (IntelliJ 2024.1 built on Java 21)

#### Scenario: Full test suite passes on Java 21
- **WHEN** ./gradlew test is executed with Java 21 JDK
- **THEN** all unit tests SHALL pass without ClassNotFoundException, NoSuchMethodError, or other Java version-related failures

### Requirement: Plugin Runtime Java 21 Compatibility

The compiled plugin SHALL run correctly in IntelliJ IDEA 2024.1+ which requires Java 21 runtime.

#### Scenario: Plugin loads in IntelliJ 2024.1
- **WHEN** the plugin is installed in IntelliJ IDEA 2024.1 running on Java 21
- **THEN** the plugin SHALL load successfully without errors

#### Scenario: Plugin descriptor specifies minimum platform version
- **WHEN** plugin.xml is parsed
- **THEN** the sinceBuild attribute SHALL be set to "241" (IntelliJ 2024.1)

#### Scenario: Plugin features work on Java 21 runtime
- **WHEN** the plugin is executed in sandbox IDE via ./gradlew runIde
- **THEN** all plugin features (project detection, change management, artifact generation, validation) SHALL function correctly

### Requirement: Developer Environment Java 21 Requirement

Plugin development SHALL require Java 21 JDK to be installed and configured in the developer's environment.

#### Scenario: Build documentation specifies Java 21
- **WHEN** a developer reads the build setup documentation
- **THEN** the documentation SHALL clearly state "Requires Java 21 JDK for development"

#### Scenario: Build fails with clear message on older Java
- **WHEN** a developer attempts to build with Java 20 or earlier
- **THEN** Gradle SHALL produce a clear error message indicating Java 21 is required

#### Scenario: IDE project configuration uses Java 21
- **WHEN** the project is opened in IntelliJ IDEA
- **THEN** the IDE project SDK SHALL be configured to use Java 21

#### Scenario: CI/CD pipeline uses Java 21
- **WHEN** automated builds run in CI/CD environment
- **THEN** the build agent SHALL be configured with Java 21 JDK

