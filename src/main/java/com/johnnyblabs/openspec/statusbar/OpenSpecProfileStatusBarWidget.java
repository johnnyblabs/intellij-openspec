package com.johnnyblabs.openspec.statusbar;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.WorkflowProfileService;
import com.johnnyblabs.openspec.services.WorkflowProfileSwitchService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Status bar widget that displays the active OpenSpec workflow profile and lets the
 * user open a popup to switch profiles or open the Settings panel.
 *
 * <p>Label format:
 * <ul>
 *   <li>{@code OpenSpec: core} — fixed core profile (5 workflows)</li>
 *   <li>{@code OpenSpec: custom · N workflows} — variable custom profile</li>
 *   <li>{@code OpenSpec: core (fallback)} — CLI unavailable</li>
 * </ul>
 *
 * <p>Click opens a list popup with: the active profile (selected), the other preset,
 * a "Available in custom" reveal listing workflows the user would gain by switching,
 * an "Edit in Settings…" link, and an "About profiles" docs link.
 */
public final class OpenSpecProfileStatusBarWidget implements StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {

    public static final String ID = "OpenSpec.ProfileWidget";

    /** Workflows that the custom profile may add beyond core. Source: OpenSpec 1.2.0+ docs. */
    static final Set<String> EXPANDED_WORKFLOWS =
            Set.of("new", "continue", "ff", "verify", "bulk-archive", "onboard");

    public static final String DOCS_URL =
            "https://github.com/johnnyblabs/intellij-openspec/blob/main/scripts/docs/wiki/Workflow-Profiles.md";
    private static final String EDIT_IN_SETTINGS = "Edit in Settings…";
    private static final String ABOUT_PROFILES = "About profiles…";
    private static final String AVAILABLE_IN_CUSTOM_HEADER = "Available in custom:";

    private final Project project;
    private StatusBar statusBar;

    public OpenSpecProfileStatusBarWidget(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String ID() {
        return ID;
    }

    @Override
    public @Nullable WidgetPresentation getPresentation() {
        return this;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        this.statusBar = statusBar;
        // Pre-warm the cache off the EDT so the first render shows real data.
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            WorkflowProfileService service = project.getService(WorkflowProfileService.class);
            if (service != null) {
                service.getActiveWorkflows(); // forces lazy resolve via CLI
            }
            ApplicationManager.getApplication().invokeLater(() -> {
                StatusBar bar = this.statusBar;
                if (bar != null) {
                    bar.updateWidget(ID);
                }
            });
        });
    }

    @Override
    public void dispose() {
        statusBar = null;
    }

    @Override
    public @Nullable String getSelectedValue() {
        WorkflowProfileService service = project.getService(WorkflowProfileService.class);
        if (service == null) return "OpenSpec";

        String profileName = service.getActiveProfileName();
        int workflowCount = service.getActiveWorkflows().size();
        return formatLabel(profileName, workflowCount, isCliAvailable());
    }

    /**
     * Builds the status bar label text. Pure function — testable without
     * IntelliJ Platform mocks.
     */
    static String formatLabel(String profileName, int workflowCount, boolean cliAvailable) {
        if (!cliAvailable) {
            return "OpenSpec: " + profileName + " (fallback)";
        }
        if ("core".equals(profileName)) {
            return "OpenSpec: core";
        }
        return "OpenSpec: " + profileName + " · " + workflowCount + " workflows";
    }

    @Override
    public @Nullable String getTooltipText() {
        WorkflowProfileService service = project.getService(WorkflowProfileService.class);
        if (service == null) return null;

        Set<String> workflows = service.getActiveWorkflows();
        String name = service.getActiveProfileName();
        StringBuilder html = new StringBuilder("<html>Profile: <b>")
                .append(name).append("</b><br>")
                .append(workflows.size()).append(" workflows: ")
                .append(String.join(", ", workflows));
        if (!isCliAvailable()) {
            html.append("<br><i>OpenSpec CLI not detected — using core defaults.</i>");
        } else {
            html.append("<br>Click to switch.");
        }
        html.append("</html>");
        return html.toString();
    }

    @Override
    public @Nullable ListPopup getPopup() {
        return JBPopupFactory.getInstance().createListPopup(buildPopupStep());
    }

    private @NotNull PopupItemStep buildPopupStep() {
        WorkflowProfileService service = project.getService(WorkflowProfileService.class);
        String activeProfile = service != null ? service.getActiveProfileName() : "core";
        Set<String> activeWorkflows = service != null ? service.getActiveWorkflows() : Set.of();

        List<String> items = new ArrayList<>();
        items.add(formatActiveItem(activeProfile, activeWorkflows.size()));
        String otherPreset = "core".equals(activeProfile) ? "custom" : "core";
        items.add(formatInactiveItem(otherPreset));

        if ("core".equals(activeProfile)) {
            Set<String> diff = new LinkedHashSet<>(EXPANDED_WORKFLOWS);
            diff.removeAll(activeWorkflows);
            if (!diff.isEmpty()) {
                items.add(AVAILABLE_IN_CUSTOM_HEADER + " " + String.join(", ", diff));
            }
        }

        items.add(EDIT_IN_SETTINGS);
        items.add(ABOUT_PROFILES);

        return new PopupItemStep(items, activeProfile);
    }

    private static String formatActiveItem(String name, int workflowCount) {
        if ("core".equals(name)) {
            return "● " + name + "  (active)";
        }
        return "● " + name + " · " + workflowCount + " workflows  (active)";
    }

    private static String formatInactiveItem(String name) {
        return "○ " + name;
    }

    private boolean isCliAvailable() {
        CliDetectionService cli = project.getService(CliDetectionService.class);
        return cli != null && cli.isAvailable();
    }

    /**
     * ListPopupStep that handles the popup actions. Profile-switch items currently no-op;
     * full switching (with the post-switch update prompt) is wired in section 6 by
     * delegating to a shared profile-switch service.
     */
    private final class PopupItemStep extends BaseListPopupStep<String> {

        private final String activeProfile;

        PopupItemStep(List<String> items, String activeProfile) {
            super("OpenSpec Workflow Profile", items);
            this.activeProfile = activeProfile;
        }

        @Override
        public @Nullable ListSeparator getSeparatorAbove(String value) {
            if (value.startsWith(AVAILABLE_IN_CUSTOM_HEADER) || EDIT_IN_SETTINGS.equals(value)) {
                return new ListSeparator();
            }
            return null;
        }

        @Override
        public boolean isSelectable(String value) {
            // The "Available in custom: …" reveal is informational, not selectable.
            return !value.startsWith(AVAILABLE_IN_CUSTOM_HEADER);
        }

        @Override
        public @Nullable PopupStep<?> onChosen(String selectedValue, boolean finalChoice) {
            if (EDIT_IN_SETTINGS.equals(selectedValue)) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "OpenSpec");
            } else if (ABOUT_PROFILES.equals(selectedValue)) {
                BrowserUtil.browse(DOCS_URL);
            } else if (selectedValue.startsWith("○ ")) {
                // Inactive profile entry — switch to it via the shared service.
                String targetProfile = extractProfileNameFromInactiveItem(selectedValue);
                if (targetProfile != null && !targetProfile.equals(activeProfile)) {
                    switchToProfile(targetProfile);
                }
            }
            // Active profile (●), separator, and "Available in custom" lines: no-op.
            return FINAL_CHOICE;
        }
    }

    private void switchToProfile(String targetProfile) {
        WorkflowProfileSwitchService switchService = project.getService(WorkflowProfileSwitchService.class);
        if (switchService == null) return;
        WorkflowProfileSwitchService.SwitchResult result = switchService.switchProfile(targetProfile);
        if (result.outcome() == WorkflowProfileSwitchService.Outcome.SWITCHED
                || result.outcome() == WorkflowProfileSwitchService.Outcome.CLI_UNAVAILABLE) {
            // Refresh the widget label/tooltip to reflect the new active profile.
            StatusBar bar = statusBar;
            if (bar != null) bar.updateWidget(ID);
        }
        if (result.outcome() == WorkflowProfileSwitchService.Outcome.SWITCHED) {
            switchService.promptAndRunUpdateIfConfirmed(targetProfile);
        }
    }

    /** Extracts the profile name from an inactive popup item like "○ custom". */
    static String extractProfileNameFromInactiveItem(String item) {
        if (item == null || !item.startsWith("○ ")) return null;
        return item.substring(2).trim();
    }
}
