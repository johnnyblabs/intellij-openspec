package com.johnnyblabs.openspec.toolwindow;

import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class OpenSpecConsolePanel extends JPanel {

    private final ConsoleView consoleView;

    public OpenSpecConsolePanel(Project project) {
        super(new BorderLayout());
        consoleView = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project).getConsole();
        add(consoleView.getComponent(), BorderLayout.CENTER);
        consoleView.print("CLI output will appear here when commands are executed.\n",
                ConsoleViewContentType.SYSTEM_OUTPUT);
    }

    public void printCommand(String command) {
        consoleView.print("$ " + command + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    }

    public void printOutput(String text) {
        consoleView.print(text, ConsoleViewContentType.NORMAL_OUTPUT);
    }

    public void printError(String text) {
        consoleView.print(text, ConsoleViewContentType.ERROR_OUTPUT);
    }

    public void printWarning(String text) {
        consoleView.print(text, ConsoleViewContentType.LOG_WARNING_OUTPUT);
    }

    public void printInfo(String text) {
        consoleView.print(text, ConsoleViewContentType.LOG_INFO_OUTPUT);
    }

    public void printSystem(String text) {
        consoleView.print(text + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    }

    /** Prints {@code text} in the console content type mapped from {@code severity}. */
    public void printSeverity(ValidationIssue.Severity severity, String text) {
        consoleView.print(text, contentTypeForSeverity(severity));
    }

    /**
     * Maps a validation severity to a theme-driven console content type. There is no
     * built-in {@code WARNING_OUTPUT}, so warnings/infos reuse the console log-filter
     * attributes ({@code LOG_WARNING_OUTPUT}/{@code LOG_INFO_OUTPUT}) — all three resolve
     * through the active color scheme, so no {@code JBColor} is hardcoded. Package-visible
     * so the mapping is unit-testable in one place.
     */
    static ConsoleViewContentType contentTypeForSeverity(ValidationIssue.Severity severity) {
        return switch (severity) {
            case ERROR -> ConsoleViewContentType.ERROR_OUTPUT;
            case WARNING -> ConsoleViewContentType.LOG_WARNING_OUTPUT;
            case INFO -> ConsoleViewContentType.LOG_INFO_OUTPUT;
        };
    }

    /**
     * Prints {@code linkText} as a hyperlink that opens {@code filePath} at
     * {@code oneBasedLine} when the path resolves to a file on disk, otherwise prints it
     * as plain {@code fallbackType} text with no (dead) link. Resolution and the 1-based →
     * 0-based line mapping are factored into {@link #resolveLocation} so the wiring is
     * testable without a live editor.
     */
    public void printFileHyperlink(Project project, String filePath, int oneBasedLine,
                                   String linkText, ConsoleViewContentType fallbackType) {
        ResolvedLocation location = resolveLocation(project, filePath, oneBasedLine);
        if (location.isResolved()) {
            consoleView.printHyperlink(linkText,
                    new OpenFileHyperlinkInfo(project, location.file(), location.zeroBasedLine()));
        } else {
            consoleView.print(linkText, fallbackType);
        }
    }

    /**
     * Convenience overload of {@link #printFileHyperlink} whose plain-text fallback carries
     * the content type mapped from {@code fallbackSeverity}, so a link that fails to resolve
     * still renders in its severity color.
     */
    public void printFileHyperlink(Project project, String filePath, int oneBasedLine,
                                   String linkText, ValidationIssue.Severity fallbackSeverity) {
        printFileHyperlink(project, filePath, oneBasedLine, linkText,
                contentTypeForSeverity(fallbackSeverity));
    }

    /**
     * A resolved issue location: the {@link VirtualFile} the path points at (or {@code null}
     * when it does not resolve to a file on disk) and the editor-ready 0-based line.
     */
    record ResolvedLocation(@Nullable VirtualFile file, int zeroBasedLine) {
        boolean isResolved() {
            return file != null;
        }
    }

    /**
     * Resolves an issue's file path to a {@link VirtualFile} (or {@code null}) and maps its
     * 1-based line to the editor's 0-based line. Absolute paths resolve directly; an
     * {@code openspec/}-relative path resolves against the project base first. Uses
     * {@link LocalFileSystem#findFileByPath} (a VFS cache lookup — EDT-safe, no read action
     * and no blocking refresh); CLI {@code type/id} pseudo-paths do not resolve and return
     * a {@code null} file so the caller degrades to plain text. Package-visible for tests.
     */
    static ResolvedLocation resolveLocation(Project project, String filePath, int oneBasedLine) {
        return new ResolvedLocation(resolveFile(filePath, project == null ? null : project.getBasePath()),
                toZeroBasedLine(oneBasedLine));
    }

    /**
     * Resolves {@code filePath} to a {@link VirtualFile} (or {@code null}): an absolute path
     * directly, an {@code openspec/}-relative path against {@code basePath}. Package-visible
     * with an explicit {@code basePath} so both resolution branches are testable with a
     * controllable base directory.
     */
    static VirtualFile resolveFile(String filePath, String basePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        LocalFileSystem lfs = LocalFileSystem.getInstance();
        VirtualFile file = lfs.findFileByPath(filePath);
        if (file == null && basePath != null && !filePath.startsWith("/")) {
            file = lfs.findFileByPath(basePath + "/" + filePath);
        }
        return file;
    }

    /**
     * Maps a validator's 1-based line to the editor's 0-based line, guarding non-positive
     * lines (CLI issues carry line 0) to 0. {@link OpenFileHyperlinkInfo} is 0-based (the
     * platform's {@code RegexpFilter} subtracts 1 from its 1-based match). Pure — no platform.
     */
    static int toZeroBasedLine(int oneBasedLine) {
        return oneBasedLine > 0 ? oneBasedLine - 1 : 0;
    }

    public void clear() {
        consoleView.clear();
    }

    public ConsoleView getConsoleView() {
        return consoleView;
    }
}
