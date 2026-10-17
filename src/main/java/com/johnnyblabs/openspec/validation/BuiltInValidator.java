package com.johnnyblabs.openspec.validation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.OpenSpecConfig;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.ConfigService;
import com.johnnyblabs.openspec.services.SchemaService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import com.johnnyblabs.openspec.version.VersionSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service(Service.Level.PROJECT)
public final class BuiltInValidator {

    private static final Pattern TITLE_PATTERN = Pattern.compile("^# .+", Pattern.MULTILINE);
    private static final Pattern REQUIREMENT_PATTERN = com.johnnyblabs.openspec.util.SpecPatterns.REQUIREMENT_HEADER;
    // CLI parity (all generations): only SHALL/MUST satisfy the requirement-keyword rule.
    // SHOULD/MAY never satisfied `openspec validate`; accepting them made the plugin laxer
    // than the client it wraps. "SHALL NOT" still satisfies via the SHALL word match.
    private static final Pattern RFC_KEYWORD_PATTERN = Pattern.compile("\\b(SHALL|MUST)\\b");
    private static final Pattern SCENARIO_PATTERN = Pattern.compile("^#{4} Scenario:.+", Pattern.MULTILINE);
    private static final Pattern CLAUSE_PATTERN = Pattern.compile("^-\\s+\\*{0,2}(GIVEN|WHEN|THEN|AND)\\*{0,2}\\b", Pattern.MULTILINE);
    private static final Pattern DELTA_SECTION_PATTERN = Pattern.compile("^## (ADDED|MODIFIED|REMOVED|RENAMED)", Pattern.MULTILINE);
    private static final Pattern FULL_SPEC_PATTERN = Pattern.compile("^## (Requirements|Purpose)", Pattern.MULTILINE);
    private static final Pattern RENAMED_ENTRY_PATTERN = com.johnnyblabs.openspec.util.SpecPatterns.RENAMED_PAIR;
    // REMOVED metadata markers, tolerant of both bold forms: **Reason:** (colon inside) and **Reason**: (colon outside).
    private static final Pattern REMOVED_REASON_PATTERN = Pattern.compile("\\*\\*\\s*Reason\\s*:?\\s*\\*\\*", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMOVED_MIGRATION_PATTERN = Pattern.compile("\\*\\*\\s*Migration\\s*:?\\s*\\*\\*", Pattern.CASE_INSENSITIVE);

    private final Project project;

    public BuiltInValidator(Project project) {
        this.project = project;
    }

    public ValidationResult validateAll() {
        List<ValidationIssue> issues = new ArrayList<>();
        issues.addAll(validateConfig().issues());
        issues.addAll(validateSpecs().issues());
        issues.addAll(validateChanges().issues());
        boolean passed = issues.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
        return new ValidationResult(passed, issues, "built-in");
    }

    public ValidationResult validateSpecs() {
        List<ValidationIssue> issues = new ArrayList<>();
        VirtualFile specsDir = OpenSpecFileUtil.getSpecsDir(project);
        if (specsDir == null || !specsDir.exists()) {
            return new ValidationResult(true, issues, "built-in");
        }
        validateSpecsRecursive(specsDir, issues);
        boolean passed = issues.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
        return new ValidationResult(passed, issues, "built-in");
    }

    private void validateSpecsRecursive(VirtualFile dir, List<ValidationIssue> issues) {
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                validateSpecsRecursive(child, issues);
            } else if ("spec.md".equals(child.getName())) {
                validateSpecFile(child, issues);
            }
        }
    }

    public void validateSpecFilePublic(VirtualFile file, List<ValidationIssue> issues) {
        validateSpecFile(file, issues);
    }

    private void validateSpecFile(VirtualFile file, List<ValidationIssue> issues) {
        String raw = readFile(file);
        if (raw == null) return;
        validateSpecContent(raw, file.getPath(), issues);
    }

    /**
     * Content-level main-spec validation, extracted so {@code BuiltInValidatorRulesTest} can drive the
     * REAL rules on a raw string instead of a re-implemented mirror (which would test its own copy, not
     * production). Fence-masks internally; {@code path} is used only for issue locations. Static +
     * package-visible because the logic is stateless.
     */
    static void validateSpecContent(String rawContent, String path, List<ValidationIssue> issues) {
        // All structural matching runs on the fence-masked form (offsets preserved).
        String content = maskFences(rawContent);

        // Should have a title — WARNING, not ERROR. The CLI requires no `# Title` H1 (it derives
        // the spec name from the directory; its structural gate is `## Purpose`/`## Requirements`),
        // so erroring here would be stricter than the client we wrap.
        if (!TITLE_PATTERN.matcher(content).find()) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, 1,
                    "Spec file should have a '# Title' heading", "spec-title-required"));
        }

        // Must have at least one requirement
        if (!REQUIREMENT_PATTERN.matcher(content).find()) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, 1,
                    "Spec file must have at least one '### Requirement:' section", "spec-requirement-required"));
        }

        // CLI 1.8 parity (upstream #1484): a main spec that declares the same `### Requirement:` name
        // more than once is an ERROR — a collision lets delta reconciliation silently discard one block
        // while updating another. The CLI dedups only headers INSIDE the `## Requirements` section
        // (headers elsewhere are routed to a different diagnostic and never deduped), matches names
        // exactly (case- and interior-whitespace-sensitive, exterior trimmed), and flags each later
        // occurrence of a repeated name against the first (N occurrences → N−1 errors). Detection is
        // scoped to that section here so the fallback is not more restrictive than the CLI; the match
        // runs on the fence-masked content, so a `### Requirement:` inside a code fence does not count.
        int reqSectionStart = -1;
        int reqSectionEnd = content.length();
        Matcher reqSection = Pattern.compile("(?im)^##\\s+Requirements\\s*$").matcher(content);
        if (reqSection.find()) {
            reqSectionStart = reqSection.end();
            Matcher nextTopHeader = Pattern.compile("(?m)^##(?!#)").matcher(content);
            if (nextTopHeader.find(reqSectionStart)) {
                reqSectionEnd = nextTopHeader.start();
            }
        }
        Map<String, Integer> firstRequirementLine = new HashMap<>();

        // Check each requirement section for RFC 2119 keywords, scenarios, and clause structure
        Matcher reqMatcher = REQUIREMENT_PATTERN.matcher(content);
        while (reqMatcher.find()) {
            int reqStart = reqMatcher.start();
            int reqLine = lineNumberAt(content, reqStart);
            String reqHeader = reqMatcher.group(1).trim();

            // Duplicate-requirement-name detection (main-spec path only), scoped to `## Requirements`.
            if (reqSectionStart >= 0 && reqStart >= reqSectionStart && reqStart < reqSectionEnd) {
                Integer firstLine = firstRequirementLine.get(reqHeader);
                if (firstLine != null) {
                    issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, reqLine,
                            "Requirement header \"### Requirement: " + reqHeader + "\" duplicates the "
                                    + "requirement declared on line " + firstLine + ". Requirement names must be "
                                    + "unique so spec updates cannot discard one block while updating another.",
                            "spec-duplicate-requirement"));
                } else {
                    firstRequirementLine.put(reqHeader, reqLine);
                }
            }
            // Find the content between this requirement and the next requirement or end
            int nextReq = findNextRequirement(content, reqMatcher.end());
            String reqContent = content.substring(reqMatcher.end(), nextReq);

            if (!RFC_KEYWORD_PATTERN.matcher(reqContent).find()) {
                // CLI 1.8 parity: the missing-keyword rule is severity-conditional on the requirement
                // having a body. 1.8 demoted "must contain SHALL/MUST" to a non-failing WARNING in
                // default mode for a body-carrying requirement (reporting it valid in default and
                // valid:false only under --strict, re-promoted via CLI_MIRRORING_STRICT_WARNINGS), but
                // a requirement with NO body prose at all still ERRORs ("must contain…"). "Body" is the
                // prose before the first `#### Scenario:` header (content is already fence-masked);
                // scenarios are not body prose.
                Matcher firstScen = SCENARIO_PATTERN.matcher(reqContent);
                String reqBody = firstScen.find() ? reqContent.substring(0, firstScen.start()) : reqContent;
                if (reqBody.trim().isEmpty()) {
                    // No body prose — 1.8 still errors on a body-less requirement.
                    issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, reqLine,
                            "Requirement '" + reqHeader + "' must contain SHALL or MUST", "spec-rfc-keywords"));
                } else if (RFC_KEYWORD_PATTERN.matcher(reqMatcher.group()).find()) {
                    // Keyword present only in the header: targeted remediation, demoted to a WARNING
                    // (body present) to match 1.8's default verdict; --strict re-promotes it.
                    issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, reqLine,
                            "Requirement '" + reqHeader + "' has its RFC 2119 keyword only in the header — "
                                    + "move the keyword onto the requirement body line", "spec-rfc-keyword-in-header"));
                } else {
                    // Body present but no SHALL/MUST — non-failing WARNING in default, matching 1.8.
                    issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, reqLine,
                            "Requirement '" + reqHeader + "' should contain SHALL or MUST "
                                    + "(RFC 2119 best practice for English specs)", "spec-rfc-keywords"));
                }
            }

            // Requirement must have at least one scenario — ERROR. Empirically, `openspec validate`
            // reports a scenarioless main-spec requirement as valid:false: it fires a Zod `.min(1)`
            // schema ERROR (base.schema.js) in addition to the WARNING guide in validator.js.
            // Demoting this would make the built-in fallback LAXER than the CLI (and would break the
            // captured-CLI verdict-parity test), so it stays ERROR.
            if (!SCENARIO_PATTERN.matcher(reqContent).find()) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, reqLine,
                        "Requirement '" + reqHeader + "' must have at least one '#### Scenario:' block", "spec-scenario-required"));
            }

            // Scenario WHEN/THEN structure is an authoring hint only — INFO, not a verdict input.
            // The CLI performs no clause-structure validation (a scenario need only be non-empty),
            // so flagging it as an ERROR would be stricter than the client we wrap.
            Matcher scenMatcher = SCENARIO_PATTERN.matcher(reqContent);
            while (scenMatcher.find()) {
                int scenLine = reqLine + lineNumberAt(reqContent, scenMatcher.start()) - 1;
                String scenHeader = scenMatcher.group().replaceFirst("^#{4}\\s*Scenario:\\s*", "").trim();
                int nextScen = findNextScenarioOrEnd(reqContent, scenMatcher.end());
                String scenContent = reqContent.substring(scenMatcher.end(), nextScen);

                boolean hasWhen = Pattern.compile("\\bWHEN\\b").matcher(scenContent).find();
                boolean hasThen = Pattern.compile("\\bTHEN\\b").matcher(scenContent).find();
                if (!hasWhen || !hasThen) {
                    String missing = !hasWhen && !hasThen ? "WHEN and THEN"
                            : !hasWhen ? "WHEN" : "THEN";
                    issues.add(new ValidationIssue(ValidationIssue.Severity.INFO, path, scenLine,
                            "Scenario '" + scenHeader + "' is missing " + missing + " clause(s)", "spec-scenario-clauses"));
                }
            }
        }
    }

    public ValidationResult validateChanges() {
        List<ValidationIssue> issues = new ArrayList<>();
        ChangeService changeService = project.getService(ChangeService.class);
        for (Change change : changeService.getActiveChanges()) {
            issues.addAll(validateSingleChange(change).issues());
        }
        boolean passed = issues.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
        return new ValidationResult(passed, issues, "built-in");
    }

    public ValidationResult validateChange(String changeName) {
        ChangeService changeService = project.getService(ChangeService.class);
        for (Change change : changeService.getActiveChanges()) {
            if (changeName.equals(change.getName())) {
                return validateSingleChange(change);
            }
        }
        return new ValidationResult(true, List.of(), "built-in");
    }

    private ValidationResult validateSingleChange(Change change) {
        List<ValidationIssue> issues = new ArrayList<>();
        VersionSupport version = getVersionSupport();
        Set<String> required = version.getRequiredArtifacts();
        String changePath = change.getPath();

        if (!change.getArtifactFiles().contains("proposal.md")) {
            // Always a non-failing WARNING — a plugin-invented lint the CLI never checks. The real CLI
            // validates a proposal-less change that has a valid delta as valid (it resolves changes by
            // directory existence, not by requiring proposal.md; upstream #1182), so failing on it would
            // make the plugin more restrictive than the client it wraps. It is not in the strict
            // CLI-mirroring warning set either, so a strict run does not flip on it. Symmetric with the
            // change-artifact-missing lint below.
            issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, changePath, 1,
                    "Change '" + change.getName() + "' should have proposal.md", "change-proposal-required"));
        }

        for (String artifact : required) {
            if ("proposal".equals(artifact)) continue;
            if ("specs".equals(artifact)) continue;
            String fileName = artifact + ".md";
            if (!change.getArtifactFiles().contains(fileName)) {
                // Always a non-failing WARNING — this is a plugin-invented lint (the CLI never checks
                // tasks.md/design.md presence). A per-run strict validation fails the verdict on it via
                // the strict warnings-count-as-failures rule (OpenSpecValidateAction), not by escalating
                // the severity here. Strict is no longer a persistent setting.
                issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, changePath, 1,
                        "Change '" + change.getName() + "' should have " + fileName, "change-artifact-missing"));
            }
        }

        // Cross-validate change schema against the known-set (built-ins UNION CLI runtime), but ONLY
        // when that known-set is authoritative (the CLI is available and supports schema management).
        // When the CLI is down the set collapses to the built-in floor, so a legitimate custom fork
        // (`openspec schema fork`, listed by `openspec schemas --json`) would falsely warn — and the
        // real CLI never rejects a schema name on `validate`. Guarding keeps the plugin no stricter
        // than the CLI: a genuine typo still warns when the CLI is present to supply the real set.
        if (knownSetIsAuthoritative()
                && change.getMetadata() != null && change.getMetadata().getSchema() != null) {
            String changeSchema = change.getMetadata().getSchema();
            java.util.Set<String> known = getKnownSchemaNames();
            if (!known.contains(changeSchema)) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, changePath, 1,
                        "Change '" + change.getName() + "' uses schema '" + changeSchema +
                                "' which is not recognized. Known schemas: " + known +
                                ". " + describeSchemaSourceStatus() +
                                " If you forked this schema via `openspec schema fork`, restart the project or " +
                                "refresh schemas in Settings → Tools → OpenSpec.",
                        "change-schema-incompatible"));
            }
        }

        int deltaCount = validateDeltaSpecs(changePath, issues);
        // No-deltas ERROR (mirrors the CLI): a change whose schema requires specs and does not declare
        // skip_specs must have at least one delta spec. `deltaCount` counts the spec.md files found under
        // specs/ — a misplaced root spec.md counts as found, so a misplaced-only change gets the
        // misplaced ERROR alone, matching the CLI. A negative count means the change dir could not be
        // resolved on disk, so we do not guess (no false positive). skip_specs suppresses it exactly as
        // the CLI does (which reports a skip_specs change with no deltas valid, with an INFO note).
        boolean skipSpecs = change.getMetadata() != null && change.getMetadata().isSkipSpecs();
        // `required.contains("specs")` is defensive future-proofing: the single V1_2 baseline always
        // requires specs, so this arm is constant-true today (the CLI likewise errors regardless of the
        // declared schema). It is retained so a future config-format baseline that drops the specs
        // artifact would suppress the ERROR (a safe under-report) without a code change.
        if (deltaCount == 0 && required.contains("specs") && !skipSpecs) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, changePath, 1,
                    "Change '" + change.getName() + "' must have at least one delta. No delta specs found "
                            + "under specs/. Add a delta at specs/<capability-path>/spec.md, or set "
                            + "skip_specs: true in .openspec.yaml if this change intentionally modifies no specs.",
                    "delta-none-found"));
        }
        boolean passed = issues.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
        return new ValidationResult(passed, issues, "built-in");
    }

    /**
     * Discover and validate a change's delta spec files, mirroring the CLI's discovery rules. Returns
     * the number of {@code spec.md} files found under the change's {@code specs/} directory — a misplaced
     * root {@code spec.md} counts as found — or {@code -1} when the change directory could not be
     * resolved on disk (so the caller does not fire the no-deltas ERROR on a resolution failure).
     *
     * <p>CLI 1.7 parity (upstream #1392/#1385): a regular file named exactly {@code spec.md} directly at
     * the {@code specs/} root is a misplaced delta — the apply/archive merge only reads capability
     * folders, so a root-level file is silently dropped. Detection is path-based and content-independent.
     * A {@code spec.md} at any depth of at least one directory below {@code specs/} (including
     * multi-segment capability paths) is a valid delta and is structurally validated, matching the CLI's
     * recursive discovery.
     */
    private int validateDeltaSpecs(String changePath, List<ValidationIssue> issues) {
        // Resolve through the project's changes dir first (works on any VFS, including
        // the test fixture's temp filesystem); fall back to a local-path lookup.
        VirtualFile changeDir = null;
        VirtualFile changesDir = OpenSpecFileUtil.getChangesDir(project);
        if (changesDir != null) {
            String name = changePath.substring(changePath.lastIndexOf('/') + 1);
            changeDir = changesDir.findChild(name);
        }
        if (changeDir == null) {
            changeDir = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(changePath);
        }
        if (changeDir == null) return -1;
        VirtualFile specsDir = changeDir.findChild("specs");
        if (specsDir == null || !specsDir.exists()) return 0;

        int found = 0;

        // Misplaced-delta ERROR: a regular file named exactly `spec.md` directly at the specs/ root.
        // Path-based and content-independent, exactly matching the CLI boundary (only the root, never a
        // spec.md at depth >= 1). A *directory* named spec.md is not misplaced — it is walked below as an
        // ordinary capability folder.
        VirtualFile rootSpec = specsDir.findChild("spec.md");
        if (rootSpec != null && !rootSpec.isDirectory()) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, rootSpec.getPath(), 1,
                    "Delta spec found at specs/spec.md. Delta specs must live under a capability path "
                            + "(e.g. specs/<capability-path>/spec.md) — a file at the specs/ root is ignored "
                            + "when the change is applied or archived.", "delta-spec-misplaced"));
            found++;
        }

        // Valid deltas: a spec.md at any depth >= 1 under specs/. Recurse so multi-segment capability
        // paths (specs/<area>/<capability>/spec.md) are discovered and validated too.
        for (VirtualFile child : specsDir.getChildren()) {
            if (child.isDirectory()) {
                found += validateDeltaSpecsUnder(child, issues);
            }
        }
        return found;
    }

    /** Recursively validate every {@code spec.md} at or below {@code dir} (a capability directory). */
    private int validateDeltaSpecsUnder(VirtualFile dir, List<ValidationIssue> issues) {
        int found = 0;
        VirtualFile specFile = dir.findChild("spec.md");
        if (specFile != null && !specFile.isDirectory()) {
            String raw = readFile(specFile);
            if (raw != null) {
                validateDeltaContent(raw, specFile.getPath(), issues);
            }
            found++;
        }
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                found += validateDeltaSpecsUnder(child, issues);
            }
        }
        return found;
    }

    public ValidationResult validateConfig() {
        List<ValidationIssue> issues = new ArrayList<>();
        ConfigService configService = project.getService(ConfigService.class);
        // Force a fresh reload to pick up any VFS changes
        configService.reload();
        OpenSpecConfig config = configService.getConfig();

        VirtualFile configFile = OpenSpecFileUtil.getConfigFile(project);
        // Also try direct lookup if VFS missed it
        if (configFile == null && project.getBasePath() != null) {
            configFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(project.getBasePath() + "/openspec/config.yaml");
            if (configFile != null && config == null) {
                // Retry loading
                configService.reload();
                config = configService.getConfig();
            }
        }
        String path = configFile != null ? configFile.getPath() : "config.yaml";

        if (config == null) {
            // Upstream OpenSpec treats openspec/config.yaml as optional — its readProjectConfig
            // returns null with the comment "No config is OK" and every caller falls back to
            // defaults (schema → "spec-driven", no context, no rules). The plugin matches that
            // contract: absence of the file is not a validation issue. See the "Config validation"
            // requirement in openspec/specs/validation/spec.md.
            return new ValidationResult(true, issues, "built-in");
        }

        if (config.getSchema() == null || config.getSchema().isEmpty()) {
            // INFO, not WARNING/ERROR. `openspec validate` never reads config.yaml and is clean for a
            // missing schema — upstream tolerates its absence and defaults to `spec-driven`. A WARNING
            // squiggle on a file the CLI accepts would be stricter than the client the plugin wraps, so
            // this is an advisory-only hygiene nudge — `schema` is the one field upstream documents as
            // required, and a silent default is a real footgun if a user forks a schema and forgets to
            // point config at it.
            issues.add(new ValidationIssue(ValidationIssue.Severity.INFO, path, 1,
                    "config.yaml has no 'schema' field; OpenSpec defaults to 'spec-driven' — "
                            + "add one to be explicit", "config-schema-required"));
        } else if (knownSetIsAuthoritative()) {
            // Schema-name recognition is CLI-runtime-driven and checked ONLY when the known-set is
            // authoritative (CLI available + schema-supported). When the CLI is down the set collapses
            // to the built-in floor, so a legitimate custom fork (listed by `openspec schemas --json`)
            // would falsely warn — and the real CLI never rejects a schema name. Guarding keeps the
            // plugin no stricter than the CLI; a genuine typo still warns when the CLI supplies the set.
            java.util.Set<String> known = getKnownSchemaNames();
            if (!known.contains(config.getSchema())) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, 1,
                        "Schema '" + config.getSchema() + "' is not recognized. Known schemas: " + known +
                                ". " + describeSchemaSourceStatus() +
                                " If you forked this schema via `openspec schema fork`, restart the project or " +
                                "refresh schemas in Settings → Tools → OpenSpec.",
                        "config-schema-invalid"));
            }
        }

        // No `version:` validation. `version:` is a plugin-internal field upstream's Zod schema strips
        // and never reads — `openspec validate` is clean for ANY `version:` value — so warning on a
        // value that isn't the single V1_2 config-format baseline was stricter than the CLI (and fired
        // even on legacy 1.0.0/1.1.0 that VersionSupport.fromString routes to V1_2). The getVersion()
        // reader stays as the config-format-axis fallback in OpenSpecSettings.getEffectiveVersion.

        // No separate required-field loop: the only field upstream's Zod requires is `schema`, and a
        // missing/empty `schema` is already reported once above as `config-schema-required`. The old
        // loop over VersionSupport.getRequiredConfigFields() re-emitted a second, duplicate ERROR
        // (`config-field-required`) for the same `schema` field. getRequiredConfigFields() is retained
        // on VersionSupport for a future config-format baseline that requires a field beyond `schema`;
        // it is intentionally no longer wired to a duplicate check here.

        // `profile:` is not in upstream's Zod schema and has no reader in the plugin (OpenSpecConfig
        // .getProfile() is unused) — its absence is never a config issue.

        boolean passed = issues.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
        return new ValidationResult(passed, issues, "built-in");
    }

    private VersionSupport getVersionSupport() {
        String version = OpenSpecSettings.getInstance(project).getEffectiveVersion(project);
        return VersionSupport.fromString(version);
    }

    /**
     * Whether the schema known-set is AUTHORITATIVE — i.e. the CLI is available and supports schema
     * management, so {@link #getKnownSchemaNames()} reflects real project-local forks
     * (`openspec schemas --json`) rather than collapsing to the built-in floor. Schema-recognition
     * warnings ({@code config-schema-invalid}, {@code change-schema-incompatible}) fire only when this
     * is true; otherwise a legitimate custom fork the plugin cannot see would falsely warn, which the
     * real CLI never does.
     */
    private boolean knownSetIsAuthoritative() {
        SchemaService schemaService = project.getService(SchemaService.class);
        return schemaService != null && schemaService.isSchemaSupported();
    }

    /**
     * Returns the set of schema names the validator should treat as recognized — the
     * union of the built-in fallback and the CLI-runtime list. Falls back to the
     * built-ins alone if {@link SchemaService} is unavailable (defensive null guard).
     * See {@link SchemaService#getKnownSchemaNames()} for full semantics.
     */
    private java.util.Set<String> getKnownSchemaNames() {
        SchemaService schemaService = project.getService(SchemaService.class);
        if (schemaService == null) {
            return VersionSupport.V1_2.getValidSchemas();
        }
        return schemaService.getKnownSchemaNames();
    }

    /**
     * Returns a short human-readable phrase describing where the known-set came from,
     * for inclusion in validator warning text. Helps the user understand why a custom
     * fork they expect to see isn't being recognized.
     */
    private String describeSchemaSourceStatus() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection == null || !detection.isAvailable()) {
            return "CLI status: unavailable (only built-in schemas can be recognized — install OpenSpec CLI 1.3+ to enable custom schema detection).";
        }
        SchemaService schemaService = project.getService(SchemaService.class);
        if (schemaService != null && !schemaService.isSchemaSupported()) {
            return "CLI status: below 1.3.0 floor (only built-in schemas can be recognized — upgrade with `npm i -g @fission-ai/openspec@latest`).";
        }
        return "CLI status: available — known-set includes both built-ins and any schemas listed by `openspec schemas --json`.";
    }

    private String readFile(VirtualFile file) {
        try {
            if (ApplicationManager.getApplication() == null
                    || ApplicationManager.getApplication().isDispatchThread()) {
                return new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            }
            return ApplicationManager.getApplication().runReadAction(
                    (ThrowableComputable<String, IOException>) () -> new String(file.contentsToByteArray(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Masks fenced code blocks (backtick or tilde fences) so structural matchers do not
     * see their content — CLI 1.6 parity: a keyword, scenario header, or requirement
     * header that exists only inside a fence does not count. Every non-newline character
     * on fence lines and fenced content becomes a space, so all offsets and line numbers
     * in the masked string equal those in the original.
     */
    static String maskFences(String content) {
        StringBuilder out = new StringBuilder(content.length());
        String openMarker = null;
        int lineStart = 0;
        while (lineStart <= content.length()) {
            int lineEnd = content.indexOf('\n', lineStart);
            boolean hasNewline = lineEnd >= 0;
            if (!hasNewline) lineEnd = content.length();
            String line = content.substring(lineStart, lineEnd);
            String trimmed = line.stripLeading();
            boolean fenceLine = trimmed.startsWith("```") || trimmed.startsWith("~~~");
            boolean masked;
            if (openMarker == null) {
                if (fenceLine) {
                    openMarker = trimmed.startsWith("```") ? "```" : "~~~";
                    masked = true;
                } else {
                    masked = false;
                }
            } else {
                masked = true;
                if (fenceLine && trimmed.startsWith(openMarker)) {
                    openMarker = null;
                }
            }
            out.append(masked ? " ".repeat(line.length()) : line);
            if (hasNewline) out.append('\n');
            lineStart = lineEnd + 1;
            if (!hasNewline) break;
        }
        return out.toString();
    }

    private static int lineNumberAt(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }

    private int findNextHeading(String content, int from) {
        Pattern heading = Pattern.compile("^#{1,3} ", Pattern.MULTILINE);
        Matcher m = heading.matcher(content);
        if (m.find(from)) {
            return m.start();
        }
        return content.length();
    }

    private static int findNextRequirement(String content, int from) {
        Matcher m = REQUIREMENT_PATTERN.matcher(content);
        if (m.find(from)) {
            return m.start();
        }
        return content.length();
    }

    private static int findNextScenarioOrEnd(String content, int from) {
        Matcher m = SCENARIO_PATTERN.matcher(content);
        if (m.find(from)) {
            return m.start();
        }
        return content.length();
    }

    private static final Pattern LEVEL3_HEADER_PATTERN = Pattern.compile("^###\\s+(.*)$", Pattern.MULTILINE);

    private static void emitSkippedHeaderHints(String content, int sectionStart, String sectionContent,
                                        String sectionType, String path, List<ValidationIssue> issues) {
        Matcher h3 = LEVEL3_HEADER_PATTERN.matcher(sectionContent);
        while (h3.find()) {
            String headerLine = h3.group().trim();
            if (REQUIREMENT_PATTERN.matcher(headerLine).find()) continue; // canonical named header — parsed, not skipped
            int line = lineNumberAt(content, sectionStart + h3.start());
            String title = h3.group(1).trim();
            boolean namelessRequirement = title.matches("(?i)requirement:?");
            String message = namelessRequirement
                    ? "Header '### Requirement:' in " + sectionType + " Requirements is missing a requirement name"
                            + " and is ignored by validation — add a name, e.g. '### Requirement: <name>'"
                    : "Header '" + headerLine + "' in " + sectionType + " Requirements is not a '### Requirement:'"
                            + " header and is ignored by validation — use '### Requirement: " + title
                            + "' if it should be validated as a requirement";
            issues.add(new ValidationIssue(ValidationIssue.Severity.INFO, path, line, message, "delta-skipped-header"));
        }
    }

    /**
     * Validates one delta spec's raw content — the same rules {@link #validateChange} applies to each
     * delta file. Static + package-private so rule tests drive production logic rather than a mirror.
     * Fence-masks internally; {@code path} is used only for issue locations.
     */
    static void validateDeltaContent(String raw, String path, List<ValidationIssue> issues) {
        // All structural matching runs on the fence-masked form (offsets preserved).
        String content = maskFences(raw);
        // Skip full specs (## Requirements / ## Purpose) — only delta specs need
        // ADDED/MODIFIED/REMOVED/RENAMED.
        if (!DELTA_SECTION_PATTERN.matcher(content).find() && !FULL_SPEC_PATTERN.matcher(content).find()) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, 1,
                    "Delta spec should have ADDED, MODIFIED, REMOVED, or RENAMED sections", "delta-spec-sections"));
        }
        validateDeltaSpecStructure(content, path, issues);
    }

    private static void validateDeltaSpecStructure(String content, String path, List<ValidationIssue> issues) {
        // Find each delta section and validate requirement blocks within
        Pattern deltaSectionStart = Pattern.compile("^## (ADDED|MODIFIED|REMOVED|RENAMED) Requirements", Pattern.MULTILINE);
        Matcher sectionMatcher = deltaSectionStart.matcher(content);

        while (sectionMatcher.find()) {
            String sectionType = sectionMatcher.group(1);
            int sectionHeaderLine = lineNumberAt(content, sectionMatcher.start());
            int sectionStart = sectionMatcher.end();
            // Find end of this section (next ## heading or end of content)
            Pattern nextH2 = Pattern.compile("^## ", Pattern.MULTILINE);
            Matcher nextH2Matcher = nextH2.matcher(content);
            int sectionEnd = content.length();
            if (nextH2Matcher.find(sectionStart)) {
                sectionEnd = nextH2Matcher.start();
            }
            String sectionContent = content.substring(sectionStart, sectionEnd);

            // CLI 1.6 parity: upstream's parser skips non-canonical level-3 headers inside
            // ADDED/MODIFIED sections and reports each as an INFO advisory (never verdict-affecting).
            if ("ADDED".equals(sectionType) || "MODIFIED".equals(sectionType)) {
                emitSkippedHeaderHints(content, sectionStart, sectionContent, sectionType, path, issues);
            }

            if ("RENAMED".equals(sectionType)) {
                // RENAMED sections carry FROM:/TO: pairs (bullet or plain form), not requirement blocks.
                // Entry lines without a well-formed pair are an error; mirrors SpecSyncService.RENAMED_ENTRY.
                // An empty section (no FROM:/TO: lines, e.g. a fresh scaffold) is not: the CLI accepts it.
                if (com.johnnyblabs.openspec.util.SpecPatterns.RENAMED_ENTRY_LINE.matcher(sectionContent).find()
                        && !RENAMED_ENTRY_PATTERN.matcher(sectionContent).find()) {
                    issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, sectionHeaderLine,
                            "RENAMED section must contain at least one FROM:/TO: pair",
                            "delta-renamed-fields"));
                }
                continue;
            }

            // Find each requirement block in this section
            Matcher reqMatcher = REQUIREMENT_PATTERN.matcher(sectionContent);
            while (reqMatcher.find()) {
                int reqLine = lineNumberAt(content, sectionStart + reqMatcher.start());
                String reqHeader = reqMatcher.group(1).trim();
                int nextReq = findNextRequirement(sectionContent, reqMatcher.end());
                String reqContent = sectionContent.substring(reqMatcher.end(), nextReq);

                if ("REMOVED".equals(sectionType)) {
                    // Reason/Migration on a REMOVED block is an OpenSpec authoring convention, not an
                    // upstream rule: the @fission-ai/openspec client validates REMOVED blocks by name
                    // only and never inspects the body. So this is advisory (WARNING), not an ERROR that
                    // blocks — the plugin must not be stricter than the client it wraps.
                    boolean hasReason = REMOVED_REASON_PATTERN.matcher(reqContent).find();
                    boolean hasMigration = REMOVED_MIGRATION_PATTERN.matcher(reqContent).find();
                    if (!hasReason || !hasMigration) {
                        String missing = !hasReason && !hasMigration ? "**Reason** and **Migration**"
                                : !hasReason ? "**Reason**" : "**Migration**";
                        issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, reqLine,
                                "REMOVED requirement '" + reqHeader + "' should contain " + missing + " fields",
                                "delta-removed-fields"));
                    }
                } else {
                    // ADDED and MODIFIED requirements must have at least one scenario with WHEN/THEN
                    if (!SCENARIO_PATTERN.matcher(reqContent).find()) {
                        String detail = "MODIFIED".equals(sectionType)
                                ? "MODIFIED requirement '" + reqHeader + "' must include full updated content with at least one scenario"
                                : "ADDED requirement '" + reqHeader + "' must have at least one '#### Scenario:' block";
                        issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, reqLine,
                                detail, "delta-requirement-scenario"));
                    }
                }
            }
        }
    }
}
