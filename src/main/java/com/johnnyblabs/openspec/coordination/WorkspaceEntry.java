package com.johnnyblabs.openspec.coordination;

import org.jetbrains.annotations.Nullable;

/**
 * A coordination workspace as presented in the IDE — derived from
 * {@code openspec workspace list --json} or, in the built-in fallback, from the
 * managed-workspaces registry on disk.
 *
 * @param name           the workspace name (registry key)
 * @param path           the local path the workspace resolves to, or null if unresolved here
 * @param resolvesLocally whether the workspace resolves on this machine
 */
public record WorkspaceEntry(String name, @Nullable String path, boolean resolvesLocally) {
}
