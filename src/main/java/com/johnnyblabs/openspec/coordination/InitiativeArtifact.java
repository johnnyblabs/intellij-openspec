package com.johnnyblabs.openspec.coordination;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The files that make up an initiative on disk, at {@code <storeRoot>/initiatives/<id>/}. Used by the
 * coordination surface to open an initiative's artifacts in the editor.
 */
public enum InitiativeArtifact {
    METADATA("initiative.yaml", "Metadata"),
    REQUIREMENTS("requirements.md", "Requirements"),
    DESIGN("design.md", "Design"),
    DECISIONS("decisions.md", "Decisions"),
    QUESTIONS("questions.md", "Questions"),
    TASKS("tasks.md", "Tasks");

    private final String fileName;
    private final String displayLabel;

    InitiativeArtifact(String fileName, String displayLabel) {
        this.fileName = fileName;
        this.displayLabel = displayLabel;
    }

    public String fileName() {
        return fileName;
    }

    public String displayLabel() {
        return displayLabel;
    }

    /**
     * Resolves the on-disk path of this artifact for the given initiative, or {@code null}
     * if the initiative's root directory is unknown. Artifacts live directly in the
     * initiative's {@code root} (the CLI reports {@code <storeRoot>/initiatives/<id>/}).
     */
    @Nullable
    public Path resolvePath(InitiativeEntry initiative) {
        if (initiative.root() == null) {
            return null;
        }
        return Path.of(initiative.root(), fileName);
    }

    /**
     * Resolves this artifact's path only when it exists on disk; returns {@code null}
     * otherwise so callers can distinguish "not yet created" from a real file.
     */
    @Nullable
    public Path resolveExistingPath(InitiativeEntry initiative) {
        Path path = resolvePath(initiative);
        return path != null && Files.isRegularFile(path) ? path : null;
    }
}
