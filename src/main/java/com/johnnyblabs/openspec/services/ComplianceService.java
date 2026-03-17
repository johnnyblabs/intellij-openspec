package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.ComplianceResult;
import com.johnnyblabs.openspec.model.ComplianceResult.Category;
import com.johnnyblabs.openspec.model.DeltaSpecOperation;
import com.johnnyblabs.openspec.model.VerificationFinding;
import com.johnnyblabs.openspec.model.VerificationReport;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;

import java.util.List;
import java.io.File;

@Service(Service.Level.PROJECT)
public final class ComplianceService {

    private final Project project;

    public ComplianceService(Project project) {
        this.project = project;
    }

    public ComplianceResult checkCompliance(String changeName) {
        ComplianceResult.Builder builder = ComplianceResult.builder(changeName);

        checkArtifactCompleteness(changeName, builder);
        checkValidation(changeName, builder);
        checkSyncReadiness(changeName, builder);

        return builder.build();
    }

    private void checkArtifactCompleteness(String changeName, ComplianceResult.Builder builder) {
        VerificationService verificationService = project.getService(VerificationService.class);
        VerificationReport report = verificationService.verify(changeName);

        for (VerificationFinding finding : report.getFindings()) {
            if (finding.severity() == VerificationFinding.Severity.CRITICAL) {
                builder.addError(Category.ARTIFACT_COMPLETENESS, finding.description());
            } else if (finding.severity() == VerificationFinding.Severity.WARNING) {
                builder.addWarning(Category.ARTIFACT_COMPLETENESS, finding.description());
            }
        }
    }

    private void checkValidation(String changeName, ComplianceResult.Builder builder) {
        BuiltInValidator validator = project.getService(BuiltInValidator.class);
        ValidationResult result = validator.validateChange(changeName);

        for (ValidationIssue issue : result.issues()) {
            if (issue.severity() == ValidationIssue.Severity.ERROR) {
                builder.addError(Category.VALIDATION, issue.message());
            } else if (issue.severity() == ValidationIssue.Severity.WARNING) {
                builder.addWarning(Category.VALIDATION, issue.message());
            }
        }
    }

    private void checkSyncReadiness(String changeName, ComplianceResult.Builder builder) {
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
                String specPath = project.getBasePath() + "/openspec/specs/"
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
