package com.johnnyblabs.openspec.validation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.OpenSpecConfig;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.ConfigService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import com.johnnyblabs.openspec.version.VersionSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service(Service.Level.PROJECT)
public final class BuiltInValidator {

    private static final Pattern TITLE_PATTERN = Pattern.compile("^# .+", Pattern.MULTILINE);
    private static final Pattern REQUIREMENT_PATTERN = Pattern.compile("^### Requirement:\\s*.+", Pattern.MULTILINE);
    private static final Pattern RFC_KEYWORD_PATTERN = Pattern.compile("\\b(SHALL NOT|SHOULD NOT|SHALL|SHOULD|MAY)\\b");
    private static final Pattern SCENARIO_PATTERN = Pattern.compile("^#{4} Scenario:.+", Pattern.MULTILINE);
    private static final Pattern CLAUSE_PATTERN = Pattern.compile("^-\\s+\\*{0,2}(GIVEN|WHEN|THEN|AND)\\*{0,2}\\b", Pattern.MULTILINE);
    private static final Pattern DELTA_SECTION_PATTERN = Pattern.compile("^## (ADDED|MODIFIED|REMOVED)", Pattern.MULTILINE);
    private static final Pattern FULL_SPEC_PATTERN = Pattern.compile("^## (Requirements|Purpose)", Pattern.MULTILINE);

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
        String content = readFile(file);
        if (content == null) return;
        String path = file.getPath();
        String[] lines = content.split("\n");

        // Must have title
        if (!TITLE_PATTERN.matcher(content).find()) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, 1,
                    "Spec file must have a '# Title' heading", "spec-title-required"));
        }

        // Must have at least one requirement
        if (!REQUIREMENT_PATTERN.matcher(content).find()) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, 1,
                    "Spec file must have at least one '### Requirement:' section", "spec-requirement-required"));
        }

        // Check each requirement section for RFC 2119 keywords, scenarios, and clause structure
        Matcher reqMatcher = REQUIREMENT_PATTERN.matcher(content);
        while (reqMatcher.find()) {
            int reqStart = reqMatcher.start();
            int reqLine = lineNumberAt(content, reqStart);
            String reqHeader = reqMatcher.group().replaceFirst("^###\\s*Requirement:\\s*", "").trim();
            // Find the content between this requirement and the next requirement or end
            int nextReq = findNextRequirement(content, reqMatcher.end());
            String reqContent = content.substring(reqMatcher.end(), nextReq);

            if (!RFC_KEYWORD_PATTERN.matcher(reqContent).find()) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, reqLine,
                        "Requirement '" + reqHeader + "' must contain RFC 2119 keywords (SHALL, MUST, SHOULD, MAY)", "spec-rfc-keywords"));
            }

            // Requirement must have at least one scenario
            if (!SCENARIO_PATTERN.matcher(reqContent).find()) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, reqLine,
                        "Requirement '" + reqHeader + "' must have at least one '#### Scenario:' block", "spec-scenario-required"));
            }

            // Each scenario must have WHEN and THEN clauses
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
                    issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, scenLine,
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
        boolean strict = OpenSpecSettings.getInstance(project).isStrictValidation();
        VersionSupport version = getVersionSupport();
        Set<String> required = version.getRequiredArtifacts();
        String changePath = change.getPath();

        if (!change.getArtifactFiles().contains("proposal.md")) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, changePath, 1,
                    "Change '" + change.getName() + "' must have proposal.md", "change-proposal-required"));
        }

        for (String artifact : required) {
            if ("proposal".equals(artifact)) continue;
            if ("specs".equals(artifact)) continue;
            String fileName = artifact + ".md";
            if (!change.getArtifactFiles().contains(fileName)) {
                ValidationIssue.Severity severity = strict
                        ? ValidationIssue.Severity.ERROR : ValidationIssue.Severity.WARNING;
                issues.add(new ValidationIssue(severity, changePath, 1,
                        "Change '" + change.getName() + "' should have " + fileName, "change-artifact-missing"));
            }
        }

        // Cross-validate change schema against project version
        if (change.getMetadata() != null && change.getMetadata().getSchema() != null) {
            String changeSchema = change.getMetadata().getSchema();
            if (!version.getValidSchemas().contains(changeSchema)) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, changePath, 1,
                        "Change '" + change.getName() + "' uses schema '" + changeSchema +
                                "' which is not valid for project version " + version.getVersion() +
                                ". Valid schemas: " + version.getValidSchemas(),
                        "change-schema-incompatible"));
            }
        }

        validateDeltaSpecs(changePath, issues);
        boolean passed = issues.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
        return new ValidationResult(passed, issues, "built-in");
    }

    private void validateDeltaSpecs(String changePath, List<ValidationIssue> issues) {
        VirtualFile changeDir = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(changePath);
        if (changeDir == null) return;
        VirtualFile specsDir = changeDir.findChild("specs");
        if (specsDir == null || !specsDir.exists()) return;

        for (VirtualFile domainDir : specsDir.getChildren()) {
            if (!domainDir.isDirectory()) continue;
            VirtualFile specFile = domainDir.findChild("spec.md");
            if (specFile == null) continue;
            String content = readFile(specFile);
            if (content == null) continue;

            // Skip full specs (## Requirements / ## Purpose) — only delta specs need ADDED/MODIFIED/REMOVED
            if (!DELTA_SECTION_PATTERN.matcher(content).find() && !FULL_SPEC_PATTERN.matcher(content).find()) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, specFile.getPath(), 1,
                        "Delta spec should have ADDED, MODIFIED, or REMOVED sections", "delta-spec-sections"));
            }

            // Structural validation of delta spec requirement blocks
            validateDeltaSpecStructure(content, specFile.getPath(), issues);
        }
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
            issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, 1,
                    "config.yaml not found or could not be parsed", "config-missing"));
            return new ValidationResult(false, issues, "built-in");
        }

        if (config.getSchema() == null || config.getSchema().isEmpty()) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, 1,
                    "config.yaml must have a 'schema' field", "config-schema-required"));
        } else {
            VersionSupport version = getVersionSupport();
            if (!version.getValidSchemas().contains(config.getSchema())) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, 1,
                        "Schema '" + config.getSchema() + "' is not a recognized value. " +
                                "Valid: " + version.getValidSchemas(), "config-schema-invalid"));
            }
        }

        // Version field validation
        if (config.getVersion() == null || config.getVersion().isEmpty()) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, 1,
                    "config.yaml should have a 'version' field", "config-version-required"));
        } else if (!VersionSupport.allVersions().contains(config.getVersion())) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, 1,
                    "Version '" + config.getVersion() + "' is not recognized. " +
                            "Known versions: " + VersionSupport.allVersions(), "config-version-unknown"));
        }

        // Required config fields for declared version
        VersionSupport version = getVersionSupport();
        for (String field : version.getRequiredConfigFields()) {
            boolean present = switch (field) {
                case "schema" -> config.getSchema() != null && !config.getSchema().isEmpty();
                case "version" -> config.getVersion() != null && !config.getVersion().isEmpty();
                default -> false;
            };
            if (!present) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, 1,
                        "config.yaml requires '" + field + "' field for version " + version.getVersion(),
                        "config-field-required"));
            }
        }

        if (config.getProfile().isEmpty()) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, 1,
                    "config.yaml should have a 'profile' field", "config-profile-recommended"));
        }

        boolean passed = issues.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
        return new ValidationResult(passed, issues, "built-in");
    }

    private VersionSupport getVersionSupport() {
        String version = OpenSpecSettings.getInstance(project).getEffectiveVersion(project);
        return VersionSupport.fromString(version);
    }

    private String readFile(VirtualFile file) {
        try {
            if (ApplicationManager.getApplication() == null
                    || ApplicationManager.getApplication().isDispatchThread()) {
                return new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            }
            return ReadAction.computeCancellable(() -> new String(file.contentsToByteArray(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    private int lineNumberAt(String content, int offset) {
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

    private int findNextRequirement(String content, int from) {
        Matcher m = REQUIREMENT_PATTERN.matcher(content);
        if (m.find(from)) {
            return m.start();
        }
        return content.length();
    }

    private int findNextScenarioOrEnd(String content, int from) {
        Matcher m = SCENARIO_PATTERN.matcher(content);
        if (m.find(from)) {
            return m.start();
        }
        return content.length();
    }

    private void validateDeltaSpecStructure(String content, String path, List<ValidationIssue> issues) {
        // Find each delta section and validate requirement blocks within
        Pattern deltaSectionStart = Pattern.compile("^## (ADDED|MODIFIED|REMOVED) Requirements", Pattern.MULTILINE);
        Matcher sectionMatcher = deltaSectionStart.matcher(content);

        while (sectionMatcher.find()) {
            String sectionType = sectionMatcher.group(1);
            int sectionStart = sectionMatcher.end();
            // Find end of this section (next ## heading or end of content)
            Pattern nextH2 = Pattern.compile("^## ", Pattern.MULTILINE);
            Matcher nextH2Matcher = nextH2.matcher(content);
            int sectionEnd = content.length();
            if (nextH2Matcher.find(sectionStart)) {
                sectionEnd = nextH2Matcher.start();
            }
            String sectionContent = content.substring(sectionStart, sectionEnd);

            // Find each requirement block in this section
            Matcher reqMatcher = REQUIREMENT_PATTERN.matcher(sectionContent);
            while (reqMatcher.find()) {
                int reqLine = lineNumberAt(content, sectionStart + reqMatcher.start());
                String reqHeader = reqMatcher.group().replaceFirst("^###\\s*Requirement:\\s*", "").trim();
                int nextReq = findNextRequirement(sectionContent, reqMatcher.end());
                String reqContent = sectionContent.substring(reqMatcher.end(), nextReq);

                if ("REMOVED".equals(sectionType)) {
                    // REMOVED requirements must have Reason and Migration
                    boolean hasReason = reqContent.contains("**Reason**");
                    boolean hasMigration = reqContent.contains("**Migration**");
                    if (!hasReason || !hasMigration) {
                        String missing = !hasReason && !hasMigration ? "**Reason** and **Migration**"
                                : !hasReason ? "**Reason**" : "**Migration**";
                        issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, reqLine,
                                "REMOVED requirement '" + reqHeader + "' must contain " + missing + " fields",
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
