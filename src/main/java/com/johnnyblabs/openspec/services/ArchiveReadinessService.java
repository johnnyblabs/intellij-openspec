package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult.Category;
import com.johnnyblabs.openspec.model.DeltaSpecOperation;
import com.johnnyblabs.openspec.model.VerificationFinding;
import com.johnnyblabs.openspec.model.VerificationReport;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;

import java.util.List;
import java.io.File;

/**
 * The pre-archive <em>verify</em> orchestrator — the single check behind the tool-window Verify
 * button, the {@code OpenSpec.Verify} menu action, and the archive pre-flight. It folds three
 * dimensions into one {@link ArchiveReadinessResult}:
 * <ul>
 *   <li><b>Completeness</b> — delegated to {@link VerificationService} (artifact/task status +
 *       AI correctness). These are the soft, bypassable {@code archive_tasks_incomplete} analogue.</li>
 *   <li><b>Validation</b> — {@link BuiltInValidator}. A validation error is the hard
 *       {@code archive_validation_failed} analogue (the only state that blocks archive).</li>
 *   <li><b>Spec sync</b> — unmatched MODIFIED delta targets (warnings only; never gate).</li>
 * </ul>
 * The finding's {@link ArchiveReadinessResult.BlockKind} (derived from category+severity) is what
 * {@link ArchiveReadinessResult#classify()} folds over to pick READY / IN_PROGRESS / BLOCKED.
 *
 * <p>Distinct from {@link VerificationService}: that is the internal completeness+correctness engine;
 * this is the user-facing pre-archive readiness orchestrator that wraps it plus validation + sync.
 */
@Service(Service.Level.PROJECT)
public final class ArchiveReadinessService {

    private final Project project;

    public ArchiveReadinessService(Project project) {
        this.project = project;
    }

    /** Run the full pre-archive verify check and fold it into a three-state readiness result. */
    public ArchiveReadinessResult verify(String changeName) {
        ArchiveReadinessResult.Builder builder = ArchiveReadinessResult.builder(changeName);

        checkCompleteness(changeName, builder);
        checkValidation(changeName, builder);
        checkSyncReadiness(changeName, builder);

        return builder.build();
    }

    private void checkCompleteness(String changeName, ArchiveReadinessResult.Builder builder) {
        VerificationService verificationService = project.getService(VerificationService.class);
        VerificationReport report = verificationService.verify(changeName);
        foldCompleteness(report, builder);
    }

    private void checkValidation(String changeName, ArchiveReadinessResult.Builder builder) {
        BuiltInValidator validator = project.getService(BuiltInValidator.class);
        ValidationResult result = validator.validateChange(changeName);
        foldValidation(result, builder);
    }

    /**
     * Fold verification (completeness/correctness) findings into the readiness result. Load-bearing
     * mapping: a critical finding becomes an {@code ARTIFACT_COMPLETENESS} error — the <b>soft</b>,
     * bypassable {@code archive_tasks_incomplete} analogue — NOT a {@code VALIDATION} error. Pure and
     * static so this exact "incomplete ⇒ soft/IN_PROGRESS, not hard/BLOCKED" mapping is unit-tested
     * without a running IDE (the regression this refactor exists to prevent).
     */
    static void foldCompleteness(VerificationReport report, ArchiveReadinessResult.Builder builder) {
        for (VerificationFinding finding : report.getFindings()) {
            if (finding.severity() == VerificationFinding.Severity.CRITICAL) {
                builder.addError(Category.ARTIFACT_COMPLETENESS, finding.description());
            } else if (finding.severity() == VerificationFinding.Severity.WARNING) {
                builder.addWarning(Category.ARTIFACT_COMPLETENESS, finding.description());
            }
        }
    }

    /**
     * Fold validation issues into the readiness result. A validation error becomes a
     * {@code VALIDATION} error — the <b>hard</b> {@code archive_validation_failed} analogue, the only
     * state that blocks archive. Pure/static for the same testability reason as {@link #foldCompleteness}.
     */
    static void foldValidation(ValidationResult result, ArchiveReadinessResult.Builder builder) {
        for (ValidationIssue issue : result.issues()) {
            if (issue.severity() == ValidationIssue.Severity.ERROR) {
                builder.addError(Category.VALIDATION, issue.message());
            } else if (issue.severity() == ValidationIssue.Severity.WARNING) {
                builder.addWarning(Category.VALIDATION, issue.message());
            }
        }
    }

    private void checkSyncReadiness(String changeName, ArchiveReadinessResult.Builder builder) {
        SpecSyncService syncService = project.getService(SpecSyncService.class);
        List<DeltaSpecOperation> operations = syncService.parseDeltaSpecs(changeName);

        if (operations.isEmpty()) {
            // No delta specs — sync readiness is not applicable, passes by default
            return;
        }

        // Check that MODIFIED operations target existing capabilities
        ChangeService changeService = project.getService(ChangeService.class);
        for (DeltaSpecOperation op : operations) {
            if (op.type() == DeltaSpecOperation.OperationType.MODIFIED) {
                // Verify the target capability has a main spec
                String specPath = com.johnnyblabs.openspec.util.OpenSpecFileUtil.openspecPath(project) + "/specs/"
                        + op.capabilityName() + "/spec.md";
                File specFile = new File(specPath);
                if (!specFile.exists()) {
                    builder.addWarning(Category.SYNC_READINESS,
                            "MODIFIED operation targets capability '" + op.capabilityName()
                                    + "' but no main spec exists at openspec/specs/" + op.capabilityName() + "/spec.md");
                }
            }
        }
    }
}
