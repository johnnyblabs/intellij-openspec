package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.model.OpenSpecConfig;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.ConfigService;
import com.johnnyblabs.openspec.services.DetectionRules;
import com.johnnyblabs.openspec.services.GlobalSkillsCache;
import com.johnnyblabs.openspec.services.HomeSkills;
import com.johnnyblabs.openspec.services.ProjectSkillsScope;
import com.johnnyblabs.openspec.util.CliVersion;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One read-only line saying where the project's OpenSpec skills are, shown in Manage AI Tools and in Settings. In
 * Manage AI Tools, a project whose skills are only on the machine also gets "Install into this project" on CLI 2.1+.
 * <p>
 * The project and the home folder are read on a pooled thread; the line says "checking" until then.
 */
public final class SkillsScopeLine {

    static final String IN_PROJECT = "Skills: in this project (.agents/skills)";
    static final String ON_MACHINE = "Skills: on this machine (~/.agents/skills)";
    static final String NOT_INSTALLED = "Skills: not installed";
    static final String CHECKING = "Skills: checking…";
    static final String RECORDED = " · recorded in openspec/config.yaml";
    static final String INSTALL = "Install into this project";
    /** The first release whose init takes a scope. */
    static final String SCOPE_SINCE = "2.1.0";

    /** What the background read found. */
    @VisibleForTesting
    record State(boolean visible, ProjectSkillsScope scope, boolean recordedProject, boolean canInstall) {
    }

    private final JPanel panel = new JPanel(new BorderLayout(JBUI.scale(8), 0));
    private final JBLabel label = new JBLabel(CHECKING);
    private final @Nullable HyperlinkLabel install;
    private final Project project;

    /** @param onInstall what "Install into this project" does, or {@code null} for a line without the action */
    public SkillsScopeLine(Project project, @Nullable Runnable onInstall) {
        this.project = project;
        label.setForeground(JBColor.GRAY);
        panel.setOpaque(false);
        panel.add(label, BorderLayout.CENTER);
        if (onInstall != null) {
            install = new HyperlinkLabel(INSTALL);
            install.addHyperlinkListener(e -> onInstall.run());
            install.setVisible(false);
            panel.add(install, BorderLayout.EAST);
        } else {
            install = null;
        }
    }

    public JComponent component() {
        return panel;
    }

    /** Reads the state off the EDT, then fills the line under {@code modality}. */
    public void refresh(ModalityState modality) {
        label.setText(CHECKING);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            State state = read(project);
            ApplicationManager.getApplication().invokeLater(() -> apply(state), modality, ignored -> project.isDisposed());
        });
    }

    @VisibleForTesting
    void apply(State state) {
        panel.setVisible(state.visible());
        label.setText(text(state.scope(), state.recordedProject()));
        if (install != null) install.setVisible(state.canInstall());
    }

    /** The project's state. Reads the disk: never on the EDT. */
    static State read(Project project) {
        return read(project, GlobalSkillsCache.getInstance(project).snapshot());
    }

    /** {@link #read(Project)} with the home folder's skills already read. */
    static State read(Project project, HomeSkills home) {
        CliDetectionService cli = project.getService(CliDetectionService.class);
        String version = cli != null && cli.isAvailable() ? cli.getDetectedVersion() : null;
        String basePath = project.getBasePath();
        if (basePath == null) return new State(false, ProjectSkillsScope.NONE, false, false);
        Path base = Path.of(basePath);
        ProjectSkillsScope scope = ProjectSkillsScope.of(base, home);
        boolean visible = visible(version, DetectionRules.fromDisk(base).is2x(), home.holdsAny(".agents/skills"));
        return new State(visible, scope, recordsProjectScope(base), canInstall(scope, version));
    }

    /** Whether {@code openspec/config.yaml} records {@code skillsScope: project}; the only value upstream acts on. */
    static boolean recordsProjectScope(Path base) {
        Path config = base.resolve("openspec/config.yaml");
        if (!Files.isRegularFile(config)) return false;
        OpenSpecConfig read = ConfigService.load(() -> Files.newInputStream(config)).config();
        return read.getSkillsScope() == OpenSpecConfig.SkillsScope.PROJECT;
    }

    /** Scope only exists from 2.x: shown for CLI 2.1+, a 2.x layout, or skills installed for the machine. */
    static boolean visible(@Nullable String cliVersion, boolean project2x, boolean homeHoldsSharedSkills) {
        return (cliVersion != null && CliVersion.atLeast(cliVersion, SCOPE_SINCE)) || project2x || homeHoldsSharedSkills;
    }

    /** "Install into this project" needs a machine-scoped project and an init that takes {@code --scope}. */
    static boolean canInstall(ProjectSkillsScope scope, @Nullable String cliVersion) {
        return scope == ProjectSkillsScope.ON_MACHINE && cliVersion != null && CliVersion.atLeast(cliVersion, SCOPE_SINCE);
    }

    static String text(ProjectSkillsScope scope, boolean recordedProject) {
        String where = switch (scope) {
            case IN_PROJECT -> IN_PROJECT;
            case ON_MACHINE -> ON_MACHINE;
            case NONE -> NOT_INSTALLED;
        };
        return recordedProject ? where + RECORDED : where;
    }

    @VisibleForTesting
    String textForTest() {
        return label.getText();
    }

    @VisibleForTesting
    boolean installShownForTest() {
        return install != null && install.isVisible();
    }

    @VisibleForTesting
    boolean shownForTest() {
        return panel.isVisible();
    }
}
