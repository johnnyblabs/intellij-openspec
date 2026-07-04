package com.johnnyblabs.openspec.model;

import java.util.List;

/**
 * Result of {@code openspec schema which <name> --json}: where a schema name resolves from.
 *
 * <p>{@code source} is the CLI's resolution origin — {@code "project"}, {@code "user"}, or
 * {@code "package"} — and {@code shadows} lists the origins of same-named copies that lost
 * resolution precedence (e.g. a project fork of {@code spec-driven} shadows the package built-in).
 */
public record SchemaResolution(
        String name,
        String source,
        String path,
        List<String> shadowedSources
) {
    public boolean isShadowing() {
        return shadowedSources != null && !shadowedSources.isEmpty();
    }
}
