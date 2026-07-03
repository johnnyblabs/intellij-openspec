package com.johnnyblabs.openspec.coordination;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A locally registered OpenSpec 1.5 store — a standalone OpenSpec repo registered on the
 * machine. Derived from {@code openspec store list --json} (entry shape {@code {id, root}}) or
 * the on-disk {@code stores/registry.yaml} in the fallback path.
 *
 * <p>{@link #metadataPresent}, {@link #metadataValid}, {@link #gitRepository}, and
 * {@link #openspecRootHealthy} carry the optional {@code store doctor} health detail; they are
 * {@code null} until a doctor lookup has enriched this entry (see the lazy-doctor path in
 * {@code CoordinationService}). Non-git stores report {@code gitRepository == false}; the other
 * git subfields the CLI leaves null are simply not surfaced, so a non-git store never NPEs.
 *
 * <p>{@link #diagnostics} retains the per-store entries of the 1.5 diagnostic envelope
 * ({@code status[]}), including each ready-made {@code fix} suggestion, for read-only display.
 *
 * @param id                  the store id
 * @param root                the store root path (canonicalized by the CLI)
 * @param metadataPresent     doctor: whether store metadata is present (null if not yet checked)
 * @param metadataValid       doctor: whether store metadata is valid (null if not yet checked)
 * @param gitRepository       doctor: whether the store root is a git repository (null if not yet checked)
 * @param openspecRootHealthy doctor: whether the store's openspec-root is healthy (null if not yet checked)
 * @param diagnostics         retained diagnostic-envelope entries for this store (never null)
 */
public record StoreEntry(
        String id,
        @Nullable String root,
        @Nullable Boolean metadataPresent,
        @Nullable Boolean metadataValid,
        @Nullable Boolean gitRepository,
        @Nullable Boolean openspecRootHealthy,
        List<Diagnostic> diagnostics) {

    public StoreEntry {
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }

    /** Constructs a list-tier entry with no doctor detail yet. */
    public static StoreEntry basic(String id, @Nullable String root) {
        return new StoreEntry(id, root, null, null, null, null, List.of());
    }

    /** Returns a copy of this entry enriched with {@code store doctor} health detail and diagnostics. */
    public StoreEntry withDoctor(@Nullable Boolean present,
                                 @Nullable Boolean valid,
                                 @Nullable Boolean git,
                                 @Nullable Boolean openspecRootHealthy,
                                 List<Diagnostic> diagnostics) {
        return new StoreEntry(id, root, present, valid, git, openspecRootHealthy, diagnostics);
    }
}
