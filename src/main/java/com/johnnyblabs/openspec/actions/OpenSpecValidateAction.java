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
import java.util.Set;

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
                BuiltInValidator validator = project.getService(BuiltInValidator.class);
                CliDetectionService detection = project.getService(CliDetectionService.class);
                ValidationResult finalResult;
                if (detection != null && detection.isAvailable()) {
                    finalResult = cliOrFallback(project, validator, target,
                            () -> CliRunner.run(project, cliArgs(target)));
                } else {
                    // No CLI available — the built-in validator is the full fallback verdict.
                    finalResult = applyStrictFallbackVerdict(
                            builtInValidate(project, validator, target), target.strict());
                }

                ValidationResult result = finalResult;
                ApplicationManager.getApplication()
                        .invokeLater(() -> showValidationResults(project, result, target));
            }
        }.queue();
    }

    /**
     * Runs the CLI validation and merges it with the built-in result, or falls back to the full built-in
     * validator (with the per-run strict flip) when the CLI run throws, or answers with anything but a verdict (an
     * error response, unreadable JSON, a response without items) ({@link CliOutputParser.CliErrorResponseException}) — never a blind pass.
     * Package-private so tests can drive the routing with a captured CLI result.
     */
    static ValidationResult cliOrFallback(Project project, BuiltInValidator validator, ValidateTarget target,
                                          java.util.concurrent.Callable<CliRunner.CliResult> cli) {
        try {
            // Every run passes --json, so the output is read as JSON whatever comes before it; output that is not a
            // verdict (an error envelope, malformed or truncated JSON, no items) throws and falls back below.
            ValidationResult cliValidation = CliOutputParser.parseJsonOutput(cli.call());
            // The CLI verdict is already strict-aware (cliArgs passed --strict when the
            // run is strict); config stays non-failing, so no fallback flip is needed here.
            return combineWithCli(validator, target, cliValidation);
        } catch (Exception ex) {
            // CLI failed to run or rejected the run — fall back to the full built-in validator, never a
            // blind pass. Apply the per-run strict verdict flip to the fallback.
            return applyStrictFallbackVerdict(builtInValidate(project, validator, target), target.strict());
        }
    }

    /**
     * Rules whose WARNING mirrors a warning the real OpenSpec CLI itself emits and fails on under
     * {@code --strict} — the ONLY WARNINGs that may flip the built-in strict fallback verdict.
     *
     * <p>Contains the missing-keyword rules ({@code spec-rfc-keywords}, {@code spec-rfc-keyword-in-header}):
     * OpenSpec 1.8 demoted the "requirement must contain SHALL/MUST" rule to a WARNING in default mode
     * for a body-carrying requirement, and reports that requirement {@code valid} in default but
     * {@code valid:false} under {@code --strict} — so the built-in strict fallback must flip on it too
     * (source-verified against the real 1.8.0 CLI parity corpus). Every OTHER built-in non-config
     * WARNING stays out: it is either a plugin-invented lint the CLI never emits
     * ({@code spec-title-required}, {@code change-artifact-missing}, {@code change-schema-incompatible},
     * {@code delta-removed-fields}) or a condition the CLI reports as an ERROR rather than a warning
     * ({@code delta-spec-sections}); flipping strict on any of those would make the plugin more
     * restrictive than the client it wraps. Add a rule here ONLY when it is source-verified that
     * {@code openspec validate --strict} emits an equivalent WARNING. An allow-list is deliberate: a
     * new rule then defaults to non-flipping (laxer — the safe direction for a never-more-restrictive
     * invariant) rather than flipping (stricter).
     */
    static final Set<String> CLI_MIRRORING_STRICT_WARNINGS =
            Set.of("spec-rfc-keywords", "spec-rfc-keyword-in-header");

    /**
     * Apply the per-run strict verdict flip to a built-in fallback result (used when the CLI is
     * absent or its run failed). Under strict, a WARNING fails the verdict only when its rule is in
     * {@link #CLI_MIRRORING_STRICT_WARNINGS} — a warning the real CLI itself emits and fails on under
     * {@code --strict} (currently the missing-keyword rules, per 1.8's demotion). The plugin's own lint
     * WARNINGs (which the CLI never emits) stay out, so the fallback is never more restrictive than the
     * client it wraps. {@code config.yaml} guidance likewise never fails (config rows are
     * display-only; those rules were never in the set). Issues are NOT re-severity-ed — a warning
     * stays a WARNING; only the top-level verdict flips. {@code public} so a parity test can drive the
     * real verdict logic without shelling out (mirrors {@link #combineWithCli}).
     */
    public static ValidationResult applyStrictFallbackVerdict(ValidationResult result, boolean strict) {
        return applyStrictFallbackVerdict(result, strict, CLI_MIRRORING_STRICT_WARNINGS);
    }

    /**
     * Overload taking the flip-rule set explicitly. Production always calls the two-arg form
     * ({@link #CLI_MIRRORING_STRICT_WARNINGS}); this seam lets a test drive the flip logic against an
     * arbitrary rule set — verdict flip, issues preserved, source preserved, no re-severity —
     * independent of the production set's current membership. Package-private for tests.
     */
    static ValidationResult applyStrictFallbackVerdict(ValidationResult result, boolean strict,
                                                       Set<String> flipRules) {
        if (!strict || !result.passed()) {
            return result; // not strict, or already failing on an ERROR — nothing to flip
        }
        boolean strictPassed = result.issues().stream().noneMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule() != null && flipRules.contains(i.rule()));
        return strictPassed ? result
                : new ValidationResult(false, result.issues(), result.source());
    }

    /**
     * Combine a successful CLI verdict with the built-in validator, CLI-authoritative.
     *
     * <p>The CLI is authoritative for the artifacts it validates (specs, change deltas) — the built-in
     * validator must not red a clean CLI. For a whole-project run the built-in validator still owns
     * {@code config.yaml} (the CLI's {@code validate} never reads it), so the verdict combines the CLI
     * result with the built-in config-only result. For a single spec/change target there is no config
     * component, so the CLI verdict stands alone.
     *
     * <p>Exposed (static, no side effects beyond reading config) so a test can inject a
     * fixture-derived or synthetic {@code cliValidation} and assert the combine without shelling out
     * to the real CLI.
     */
    public static ValidationResult combineWithCli(BuiltInValidator validator, ValidateTarget target,
                                                  ValidationResult cliValidation) {
        if (target.isWholeProject()) {
            ValidationResult configResult = validator.validateConfig();
            return ValidationResult.mergeCliAuthoritative(cliValidation, configResult);
        }
        return cliValidation;
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

    /** CLI argument vector for the target's validate invocation. Package-private for tests. */
    static String[] cliArgs(ValidateTarget target) {
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
        // Per-run strict maps to the CLI's own --strict (warnings count as failures). The verdict is
        // then read from each item's `valid` field, which the CLI computes under --strict.
        if (target.strict()) {
            args.add("--strict");
        }
        return args.toArray(new String[0]);
    }

    /** The command string echoed to the console, mirroring {@link #cliArgs}. Package-private for tests. */
    static String commandLine(ValidateTarget target) {
        String base = target.isWholeProject()
                ? "openspec validate --all"
                : "openspec validate " + target.id() + " --type " + target.cliType();
        return target.strict() ? base + " --strict" : base;
    }

    /** Human label for the scoped target, used in the task title and console/notification text. */
    private static String describeTarget(ValidateTarget target) {
        return switch (target.kind()) {
            case SPEC -> "Spec `" + target.id() + "`";
            case CHANGE -> "Change `" + target.id() + "`";
            case WHOLE_PROJECT -> "whole project";
        };
    }

    /**
     * The at-a-glance balloon summary body. Pure and package-private so the strict disclosure is
     * headlessly unit-testable. Preserves the `passed (` / `failed (` tokens the uiSmoke journeys and
     * screenshot tour assert on; a strict, warnings-only failure appends the explanation AFTER the
     * paren group (never inside it), so those tokens stay intact.
     */
    static String summaryText(String scope, ValidationResult result, boolean strict) {
        if (result.passed()) {
            return scope + " passed (" + result.warningCount() + " warnings)";
        }
        String body = scope + " failed (" + result.errorCount() + " errors, " + result.warningCount() + " warnings)";
        if (strict && result.errorCount() == 0) {
            body += " — strict: warnings count as failures";
        }
        return body;
    }

    private void showValidationResults(Project project, ValidationResult result, ValidateTarget target) {
        String scope = describeTarget(target);

        // The at-a-glance balloon stays the summary surface — it never enumerates issues. A strict run
        // discloses itself: the title carries "(strict)", so the mode is never silent. The summary body
        // (built by the pure summaryText helper) preserves the `passed (`/`failed (` tokens the uiSmoke
        // journeys + screenshot tour assert on.
        String title = target.strict() ? "Validate (strict)" : "Validate";
        OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_VALIDATION, title,
                summaryText(scope, result, target.strict()),
                result.passed() ? com.intellij.notification.NotificationType.INFORMATION
                        : com.intellij.notification.NotificationType.ERROR);

        // The console is the detailed, navigable surface: grouped by file, per-severity
        // colored, with clickable file:line links for resolvable paths.
        renderToConsole(project, ValidationConsoleFormatter.format(result, scope), target);
    }

    /**
     * Walks the pure {@link ValidationConsoleFormatter.RenderPlan} onto the console, printing
     * each segment through the panel's typed helpers. Runs on the EDT (the caller's
     * {@code invokeLater} continuation); file resolution inside {@code printFileHyperlink} is a
     * VFS cache lookup, so no off-EDT pre-resolve is needed.
     */
    private void renderToConsole(Project project, ValidationConsoleFormatter.RenderPlan plan,
                                 ValidateTarget target) {
        OpenSpecConsoleService consoleService = project.getService(OpenSpecConsoleService.class);
        OpenSpecConsolePanel console = consoleService != null ? consoleService.getAndActivate() : null;

        if (console == null) {
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_VALIDATION, "Validate",
                    "Validation " + (plan.passed() ? "passed" : "failed"),
                    plan.passed() ? com.intellij.notification.NotificationType.INFORMATION
                            : com.intellij.notification.NotificationType.ERROR);
            return;
        }

        console.clear();
        console.printCommand(commandLine(target));

        // Thin executor: the link-vs-plain and severity-color decisions live in the pure,
        // headless-testable ValidationConsoleFormatter.toRenderOps; here we only emit each op.
        for (ValidationConsoleFormatter.RenderOp op : ValidationConsoleFormatter.toRenderOps(plan)) {
            if (op.hyperlink()) {
                if (op.severity() != null) {
                    console.printFileHyperlink(project, op.path(), op.oneBasedLine(),
                            op.text(), op.severity());
                } else {
                    console.printFileHyperlink(project, op.path(), op.oneBasedLine(),
                            op.text(), com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT);
                }
            } else if (op.severity() != null) {
                console.printSeverity(op.severity(), op.text());
            } else {
                console.printOutput(op.text());
            }
        }
    }
}
