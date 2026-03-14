package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.toolwindow.OpenSpecConsolePanel;
import com.johnnyblabs.openspec.toolwindow.OpenSpecConsoleService;
import com.johnnyblabs.openspec.util.CliOutputParser;
import com.johnnyblabs.openspec.util.CliRunner;
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
 */
public class OpenSpecValidateAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        new Task.Backgroundable(project, "Validating OpenSpec project", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                // Always run built-in validation
                BuiltInValidator validator = project.getService(BuiltInValidator.class);
                ValidationResult builtInResult = validator.validateAll();

                // Also run CLI validation if available
                CliDetectionService detection = project.getService(CliDetectionService.class);
                ValidationResult finalResult;
                if (detection != null && detection.isAvailable()) {
                    try {
                        // Use --json for structured output
                        List<String> args = new ArrayList<>();
                        args.add("validate");
                        args.add("--all");
                        args.add("--json");
                        CliRunner.CliResult cliResult = CliRunner.run(project,
                                args.toArray(new String[0]));
                        ValidationResult cliValidation;
                        if (cliResult.stdout() != null && cliResult.stdout().trim().startsWith("{")) {
                            cliValidation = CliOutputParser.parseJsonOutput(cliResult.stdout());
                        } else {
                            cliValidation = CliOutputParser.parseTextOutput(cliResult);
                        }
                        finalResult = ValidationResult.merge(builtInResult, cliValidation);
                    } catch (Exception ex) {
                        // CLI failed, use built-in only
                        finalResult = builtInResult;
                    }
                } else {
                    finalResult = builtInResult;
                }

                ValidationResult result = finalResult;
                ApplicationManager.getApplication()
                        .invokeLater(() -> showValidationResults(project, result));
            }
        }.queue();
    }

    private void showValidationResults(Project project, ValidationResult result) {
        StringBuilder sb = new StringBuilder();
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

        if (result.passed()) {
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_VALIDATION, "Validate",
                    "Validation passed (" + result.warningCount() + " warnings)",
                    com.intellij.notification.NotificationType.INFORMATION);
        } else {
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_VALIDATION, "Validate",
                    "Validation failed (" + result.errorCount() + " errors, " + result.warningCount() + " warnings)",
                    com.intellij.notification.NotificationType.ERROR);
        }

        showInConsole(project, sb.toString(), result.passed());
    }

    private void showInConsole(Project project, String text, boolean success) {
        OpenSpecConsoleService consoleService = project.getService(OpenSpecConsoleService.class);
        OpenSpecConsolePanel console = consoleService != null ? consoleService.getAndActivate() : null;

        if (console == null) {
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_VALIDATION, "Validate",
                    "Validation " + (success ? "passed" : "failed"),
                    success ? com.intellij.notification.NotificationType.INFORMATION : com.intellij.notification.NotificationType.ERROR);
            return;
        }

        console.clear();
        console.printCommand("openspec validate --all");
        if (success) {
            console.printOutput(text);
        } else {
            console.printError(text);
        }
    }
}
