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

        // Check each requirement section for RFC 2119 keywords
        Matcher reqMatcher = REQUIREMENT_PATTERN.matcher(content);
        while (reqMatcher.find()) {
            int reqStart = reqMatcher.start();
            int reqLine = lineNumberAt(content, reqStart);
            // Find the content between this requirement and the next heading
            int nextHeading = findNextHeading(content, reqMatcher.end());
            String reqContent = content.substring(reqMatcher.end(), nextHeading);

            if (!RFC_KEYWORD_PATTERN.matcher(reqContent).find()) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, reqLine,
                        "Requirement should contain RFC 2119 keywords (SHALL, SHOULD, MAY)", "spec-rfc-keywords"));
            }

            // Check scenarios have GIVEN/WHEN/THEN
            if (SCENARIO_PATTERN.matcher(reqContent).find() && !CLAUSE_PATTERN.matcher(reqContent).find()) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, reqLine,
                        "Scenarios should have GIVEN/WHEN/THEN clauses", "spec-scenario-clauses"));
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

            if (!DELTA_SECTION_PATTERN.matcher(content).find()) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, specFile.getPath(), 1,
                        "Delta spec should have ADDED, MODIFIED, or REMOVED sections", "delta-spec-sections"));
            }
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
}
