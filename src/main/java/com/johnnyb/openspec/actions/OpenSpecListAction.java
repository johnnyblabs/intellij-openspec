package com.johnnyb.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.johnnyb.openspec.model.Change;
import com.johnnyb.openspec.model.ChangeStatus;
import com.johnnyb.openspec.model.SpecFile;
import com.johnnyb.openspec.services.ChangeService;
import com.johnnyb.openspec.services.SpecParsingService;
import com.johnnyb.openspec.toolwindow.OpenSpecConsolePanel;
import com.johnnyb.openspec.toolwindow.OpenSpecConsoleService;
import com.johnnyb.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Lists specs, active changes, and archived changes.
 *
 * <p><b>Strategy: CLI preferred, built-in fallback.</b> List is a read-only
 * operation. When the CLI is available, its formatted output is displayed
 * directly in the console — this may include richer formatting, DAG status,
 * and information from CLI-specific features. When the CLI is not available,
 * the built-in fallback reads specs and changes from the VFS and produces
 * equivalent output.</p>
 *
 * <p><b>Value-add when CLI is present:</b> CLI list output includes DAG
 * status indicators, artifact completion percentages, and spec cross-reference
 * counts that the built-in fallback does not compute.</p>
 */
public class OpenSpecListAction extends OpenSpecCliAction {

    @Override
    protected String[] getCliArgs() {
        return new String[]{"list"};
    }

    @Override
    protected String getCommandLabel() {
        return "list";
    }

    @Override
    protected boolean handleCliMissing(Project project, AnActionEvent event) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            // File I/O on background thread
            SpecParsingService specService = project.getService(SpecParsingService.class);
            List<SpecFile> specs = specService.parseAllSpecs();

            ChangeService changeService = project.getService(ChangeService.class);
            List<Change> active = changeService.getActiveChanges();
            List<Change> archived = changeService.getArchivedChanges();

            StringBuilder sb = new StringBuilder();
            sb.append("=== Specs ===\n");
            if (specs.isEmpty()) {
                sb.append("  (none)\n");
            } else {
                for (SpecFile spec : specs) {
                    sb.append("  ").append(spec.getDomain())
                            .append(" (").append(spec.getRequirements().size()).append(" requirements)\n");
                }
            }

            sb.append("\n=== Active Changes ===\n");
            if (active.isEmpty()) {
                sb.append("  (none)\n");
            } else {
                for (Change change : active) {
                    ChangeStatus status = changeService.getStatus(change);
                    sb.append("  ").append(change.getName());
                    if (status != ChangeStatus.UNKNOWN) {
                        sb.append(" ").append(status.toLabel());
                    }
                    sb.append("\n");
                }
            }

            sb.append("\n=== Archive ===\n");
            if (archived.isEmpty()) {
                sb.append("  (none)\n");
            } else {
                for (Change change : archived) {
                    sb.append("  ").append(change.getName()).append("\n");
                }
            }

            String output = sb.toString();

            ApplicationManager.getApplication().invokeLater(() -> {
                OpenSpecConsoleService consoleService = project.getService(OpenSpecConsoleService.class);
                OpenSpecConsolePanel console = consoleService != null ? consoleService.getAndActivate() : null;

                if (console != null) {
                    console.clear();
                    console.printCommand("openspec list (built-in)");
                    console.printOutput(output);
                } else {
                    OpenSpecNotifier.info(project, "OpenSpec list generated (built-in)");
                }
            });
        });

        return true;
    }
}
