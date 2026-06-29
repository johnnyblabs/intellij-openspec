package com.johnnyblabs.openspec.coordination;

import org.jetbrains.annotations.Nullable;

/**
 * A locally registered context store — derived from
 * {@code openspec context-store list --json} (entry shape {@code {id, root, metadataPath?}})
 * or the on-disk context-stores registry in the fallback path.
 *
 * <p>{@link #metadataPresent}, {@link #metadataValid}, and {@link #gitRepository} carry the
 * optional {@code doctor} health detail; they are {@code null} until a doctor lookup has been
 * performed for this store (see the lazy-doctor path in {@code CoordinationService}).
 *
 * @param id             the context store id
 * @param root           the store root path
 * @param metadataPath   path to the store metadata file, or null if unknown
 * @param metadataPresent doctor: whether metadata is present (null if not yet checked)
 * @param metadataValid   doctor: whether metadata is valid (null if not yet checked)
 * @param gitRepository   doctor: whether the store root is a git repository (null if not yet checked)
 */
public record ContextStoreEntry(
        String id,
        String root,
        @Nullable String metadataPath,
        @Nullable Boolean metadataPresent,
        @Nullable Boolean metadataValid,
        @Nullable Boolean gitRepository) {

    /** Constructs a list-tier entry with no doctor detail yet. */
    public static ContextStoreEntry basic(String id, String root, @Nullable String metadataPath) {
        return new ContextStoreEntry(id, root, metadataPath, null, null, null);
    }

    /** Returns a copy of this entry enriched with doctor health detail. */
    public ContextStoreEntry withDoctor(@Nullable Boolean present, @Nullable Boolean valid, @Nullable Boolean git) {
        return new ContextStoreEntry(id, root, metadataPath, present, valid, git);
    }
}
