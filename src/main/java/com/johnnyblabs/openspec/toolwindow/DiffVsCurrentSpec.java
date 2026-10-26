package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Diff vs current spec" entries the workflow panel offers for a change's specs: one per capability, each the
 * delta spec that Preview Diff compares with the current main spec. The wording says what the change does compared
 * with the current spec, never what a generation changed.
 */
public final class DiffVsCurrentSpec {

    public static final String LABEL = "Diff vs current spec";

    /** One capability's delta spec. */
    public record Entry(@NotNull String capability, @NotNull String deltaSpecPath) {
    }

    private DiffVsCurrentSpec() {
    }

    /** True when an artifact's output path is the change's specs, which is where OpenSpec reads delta specs. */
    public static boolean isSpecsOutput(@Nullable String outputPath) {
        return outputPath != null && outputPath.startsWith("specs/");
    }

    /**
     * The entries for {@code artifact}: one per capability, with the same {@code <changeDir>/specs/<capability>/spec.md}
     * path the tree and the deltas preview build. Empty unless the artifact is the done specs artifact.
     */
    public static @NotNull List<Entry> entries(@NotNull ArtifactInfo artifact, @Nullable String changeDir,
                                               @NotNull List<String> capabilities) {
        if (artifact.status() != ArtifactStatus.DONE || !isSpecsOutput(artifact.outputPath())) return List.of();
        return entries(changeDir, capabilities);
    }

    /** The entries for a change's capabilities, without checking an artifact. */
    public static @NotNull List<Entry> entries(@Nullable String changeDir, @NotNull List<String> capabilities) {
        if (changeDir == null) return List.of();
        List<Entry> entries = new ArrayList<>();
        for (String capability : capabilities) {
            entries.add(new Entry(capability, changeDir + "/specs/" + capability + "/spec.md"));
        }
        return entries;
    }

    /** A menu item's tooltip. */
    public static @NotNull String tooltip(@NotNull String capability) {
        return "What this change does to specs/" + capability + "/spec.md, compared with the current spec";
    }
}
