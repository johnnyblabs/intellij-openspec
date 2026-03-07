# Contributing

## Development Workflow

1. **Fork and clone** the repository
2. Open in IntelliJ IDEA (import as Gradle project)
3. Run `./gradlew runIde` to test changes
4. Create a feature branch for your changes
5. Submit a pull request

## Code Style

### Java 17 Features
The codebase uses Java 17 features where appropriate:
- **Records** — `TreeNodeData`, `ValidationResult`, `ValidationIssue`
- **Sealed classes** — where applicable
- **Pattern matching** — `instanceof` with binding variables
- **Text blocks** — multi-line strings in templates

### Conventions
- **Service classes** — annotated with `@Service(Service.Level.PROJECT)`
- **Action classes** — extend `OpenSpecBaseAction` or `OpenSpecCliAction`
- **Model classes** — simple POJOs or records, no business logic
- **Naming** — standard Java naming; no Hungarian notation
- **Null handling** — use `@Nullable` / `@NotNull` annotations from JetBrains

### Package Organization
- New features should fit into existing packages
- Actions go in `actions/`, services in `services/`, etc.
- If a new package is needed, discuss in the PR description

## OpenSpec Dogfooding

This project uses OpenSpec itself. The `openspec/` directory contains:
- **Specs** for each domain (actions, tool-window, editor, plugin-core, validation)
- **Changes** tracking feature development

When making changes:
1. Check if an existing spec covers your area
2. If adding a new feature domain, create a new spec
3. Propose a change with `OpenSpec → Propose` before implementing
4. Use the change's task list to guide implementation

## Writing Tests

### Unit Tests
- Place in `src/test/java/com/johnnyb/openspec/`
- Use JUnit 5 (`@Test`, `@BeforeEach`, assertions from `org.junit.jupiter`)
- Name test classes `*Test.java`
- Mock IntelliJ platform services using `LightPlatformTestCase` or manual mocks

### What to Test
- **Service logic** — parsing, validation rules, status transitions
- **Model behavior** — serialization, enum conversions, computed properties
- **Utility functions** — CLI output parsing, file utilities

### Running Tests
```bash
./gradlew test
```

## Pull Request Guidelines

### PR Title
Use a concise, descriptive title:
- `Add keyboard shortcuts for common actions`
- `Fix dark theme colors in tree renderer`
- `Improve validation error messages`

### PR Description
Include:
- **What** the change does
- **Why** it's needed
- **How** to test it
- Related issue numbers

### Review Checklist
- [ ] Code compiles without warnings
- [ ] Tests pass (`./gradlew test`)
- [ ] Plugin runs correctly (`./gradlew runIde`)
- [ ] No IntelliJ platform deprecation warnings
- [ ] New features update relevant specs in `openspec/`

## Issue Labels

See the project's issue tracker for current labels. Key categories:
- **Type:** `enhancement`, `bug`, `documentation`, `refactor`, `testing`
- **Domain:** `ai`, `cli-integration`, `validation`, `editor`, `toolwindow`
- **Priority:** `priority:high`, `priority:medium`, `priority:low`
- **Onboarding:** `good-first-issue`

## Getting Help

- Check [[Troubleshooting]] for common development issues
- Review [[Architecture-Overview]] for system design
- Read [[Service-Layer]] to understand service interactions

---

**Previous:** [[Build-and-Dev-Setup]] | **Home:** [[Home]]
