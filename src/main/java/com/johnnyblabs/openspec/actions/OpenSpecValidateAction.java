package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.toolwindow.OpenSpecConsolePanel;
import com.johnnyblabs.openspec.toolwindow.OpenSpecConsoleService;
import com.johnnyblabs.openspec.util.CliOutputParser;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates the OpenSpec project structure, specs, and changes.
 *
 * <p><b>Strategy: Built-in always + CLI enhancement.</b> Validate is a read
 * operation. The built-in validator always runs first, checking config, spec
 * format (titles, RFC 2119 keywords, scenarios), and change completeness.
 * If the CLI is available, its validation output is merged with the built-in
 * results — the CLI may catch additional issues (custom rules, schema
 * extensions) that the built-in validator doesn't cover.</p>
 *
 * <p><b>Value-add when CLI is present:</b> CLI validation can enforce custom
 * rules defined in {@code config.yaml}, validate against newer schema versions
 * before the plugin is updated, and check cross-references between specs and
 * changes that require full-graph analysis.</p>
 *
 * <p><b>Scoping.</b> The main-menu invocation always validates the whole project.
 * {@link OpenSpecValidateFromProjectViewAction} reuses {@link #runValidation} with a
 * {@link ValidateTarget} resolved from the Project-View selection, so the same
 * built-in+CLI merge pipeline runs scoped to a single change or spec.</p>
 */
public class OpenSpecValidateAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        runValidation(project, ValidateTarget.wholeProject());
    }

    /**
     * Runs the built-in-plus-CLI validate pipeline in a background task, scoped to
     * {@code target}. {@link ValidateTarget.Kind#WHOLE_PROJECT} reproduces the classic
     * whole-project behavior ({@code validateAll()} + {@code validate --all --json});
     * {@code SPEC}/{@code CHANGE} run the built-in single-target validation always, plus
     * the CLI's {@code validate <id> --type spec|change --json} when available, merged.
     */
    protected void runValidation(Project project, ValidateTarget target) {
        new Task.Backgroundable(project, "Validating OpenSpec " + describeTarget(target), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                // Always run built-in validation, scoped to the target.
                BuiltInValidator validator = project.getService(BuiltInValidator.class);
                ValidationResult builtInResult = builtInValidate(project, validator, target);

                // Also run CLI validation if available.
                CliDetectionService detection = project.getService(CliDetectionService.class);
                ValidationResult finalResult;
                if (detection != null && detection.isAvailable()) {
                    try {
                        CliRunner.CliResult cliResult = CliRunner.run(project, cliArgs(target));
                        ValidationResult cliValidation;
                        if (cliResult.stdout() != null && cliResult.stdout().trim().startsWith("{")) {
                            cliValidation = CliOutputParser.parseJsonOutput(cliResult.stdout());
                        } else {
                            cliValidation = CliOutputParser.parseTextOutput(cliResult);
                        }
                        finalResult = ValidationResult.merge(builtInResult, cliValidation);
                    } catch (Exception ex) {
                        // CLI failed, use built-in only.
                        finalResult = builtInResult;
                    }
                } else {
                    finalResult = builtInResult;
                }

                ValidationResult result = finalResult;
                ApplicationManager.getApplication()
                        .invokeLater(() -> showValidationResults(project, result, target));
            }
        }.queue();
    }

    /** Built-in validation scoped to the target. Package-private for routing tests. */
    static ValidationResult builtInValidate(Project project, BuiltInValidator validator,
                                            ValidateTarget target) {
        switch (target.kind()) {
            case CHANGE:
                return validator.validateChange(target.id());
            case SPEC: {
                List<ValidationIssue> issues = new ArrayList<>();
                VirtualFile specsDir = OpenSpecFileUtil.getSpecsDir(project);
                if (specsDir != null) {
                    VirtualFile capDir = specsDir.findChild(target.id());
                    if (capDir != null) {
                        VirtualFile specFile = capDir.findChild("spec.md");
                        if (specFile != null) {
                            validator.validateSpecFilePublic(specFile, issues);
                        }
                    }
                }
                boolean passed = issues.stream()
                        .noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
                return new ValidationResult(passed, issues, "built-in");
            }
            case WHOLE_PROJECT:
            default:
                return validator.validateAll();
        }
    }

    /** CLI argument vector for the target's validate invocation. */
    private static String[] cliArgs(ValidateTarget target) {
        List<String> args = new ArrayList<>();
        args.add("validate");
        if (target.isWholeProject()) {
            args.add("--all");
        } else {
            args.add(target.id());
            args.add("--type");
            args.add(target.cliType());
        }
        args.add("--json");
        return args.toArray(new String[0]);
    }

    /** The command string echoed to the console, mirroring {@link #cliArgs}. */
    private static String commandLine(ValidateTarget target) {
        if (target.isWholeProject()) {
            return "openspec validate --all";
        }
        return "openspec validate " + target.id() + " --type " + target.cliType();
    }

    /** Human label for the scoped target, used in the task title and console/notification text. */
    private static String describeTarget(ValidateTarget target) {
        return switch (target.kind()) {
            case SPEC -> "Spec `" + target.id() + "`";
            case CHANGE -> "Change `" + target.id() + "`";
            case WHOLE_PROJECT -> "whole project";
        };
    }

    private void showValidationResults(Project project, ValidationResult result, ValidateTarget target) {
        StringBuilder sb = new StringBuilder();
        sb.append("Target: ").append(describeTarget(target)).append("\n");
        sb.append("Validation ").append(result.passed() ? "PASSED" : "FAILED");
        sb.append(" (source: ").append(result.source()).append(")\n");
        sb.append("Errors: ").append(result.errorCount());
        sb.append(", Warnings: ").append(result.warningCount()).append("\n\n");

        for (ValidationIssue issue : result.issues()) {
            sb.append("[").append(issue.severity()).append("] ");
            if (issue.filePath() != null && !issue.filePath().isEmpty()) {
                String shortPath = issue.filePath();
                int idx = shortPath.indexOf("openspec/");
                if (idx >= 0) shortPath = shortPath.substring(idx);
                sb.append(shortPath);
                if (issue.line() > 0) sb.append(":").append(issue.line());
                sb.append(" — ");
            }
            sb.append(issue.message());
            if (issue.rule() != null && !issue.rule().isEmpty()) {
                sb.append(" [").append(issue.rule()).append("]");
            }
            sb.append("\n");
        }

        String scope = describeTarget(target);
        if (result.passed()) {
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_VALIDATION, "Validate",
                    scope + " passed (" + result.warningCount() + " warnings)",
                    com.intellij.notification.NotificationType.INFORMATION);
        } else {
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_VALIDATION, "Validate",
                    scope + " failed (" + result.errorCount() + " errors, " + result.warningCount() + " warnings)",
                    com.intellij.notification.NotificationType.ERROR);
        }

        showInConsole(project, sb.toString(), result.passed(), target);
    }

    private void showInConsole(Project project, String text, boolean success, ValidateTarget target) {
        OpenSpecConsoleService consoleService = project.getService(OpenSpecConsoleService.class);
        OpenSpecConsolePanel console = consoleService != null ? consoleService.getAndActivate() : null;

        if (console == null) {
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_VALIDATION, "Validate",
                    "Validation " + (success ? "passed" : "failed"),
                    success ? com.intellij.notification.NotificationType.INFORMATION : com.intellij.notification.NotificationType.ERROR);
            return;
        }

        console.clear();
        console.printCommand(commandLine(target));
        if (success) {
            console.printOutput(text);
        } else {
            console.printError(text);
        }
    }
}
