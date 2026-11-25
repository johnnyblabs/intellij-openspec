package com.johnnyblabs.openspec.root;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Every user-facing sentence about where the OpenSpec root lives: the Browse tree's root label and tooltip, the
 * ignored-pointer hint and notification, the editor banner on store files, and the title of a store problem. One
 * place, so the surfaces can't drift. Pure: no IntelliJ APIs.
 *
 * <p>Words follow the CLI's own user-facing text: a store named by the project's config is {@code declared}, and
 * one from the global {@code defaultStore} is the {@code global default} (upstream's fix text says "clear the stale
 * global default"). A root found in a parent folder reads {@code parent folder}. A project's own root gets no label.
 */
public final class RootProvenance {

    public static final String PLAIN_ROOT = "OpenSpec";
    private static final String SEP = " · ";

    private RootProvenance() {
    }

    /** Whether the resolved root is the project or lies inside it: nothing to tell the user. */
    public static boolean isLocal(RootResolution r, @Nullable Path projectBase) {
        if (!r.resolved()) return true;
        if (projectBase == null) return false;
        return r.root().startsWith(RootResolver.canonical(projectBase));
    }

    /** The Browse tree's root label. */
    public static String treeLabel(RootResolution r, @Nullable Path projectBase) {
        if (isLocal(r, projectBase)) return PLAIN_ROOT;
        return PLAIN_ROOT + SEP + where(r);
    }

    /** The tree root's tooltip; {@code null} for a project's own root. */
    public static @Nullable String treeTooltip(RootResolution r, @Nullable Path projectBase) {
        if (isLocal(r, projectBase)) return null;
        return "Specs live in " + r.openspecDir() + ", outside this project. " + commitAdvice(r);
    }

    /** The editor banner on a file under a root outside the project. */
    public static String bannerText(RootResolution r) {
        String where = r.storeId() != null
                ? "OpenSpec store '" + r.storeId() + "'"
                : "the OpenSpec folder " + r.openspecDir();
        return "This file is in " + where + ", outside this project. " + commitAdvice(r);
    }

    /** The hint and notification when the project's own planning tree overrides a {@code store:} pointer. */
    public static @Nullable String ignoredPointerText(RootResolution r) {
        if (r.ignoredStoreId() == null) return null;
        return "config.yaml names store '" + r.ignoredStoreId()
                + "', but this project has its own openspec/ tree, so the store is ignored.";
    }

    /** The title of a store problem, naming the store when the problem knows it. */
    public static String problemTitle(RootResolution.Problem p) {
        return p.storeId() != null
                ? "The OpenSpec store '" + p.storeId() + "' can't be used"
                : "The OpenSpec store can't be used";
    }

    private static String where(RootResolution r) {
        if (r.storeId() != null) {
            String source = r.source() == RootResolution.Source.GLOBAL_DEFAULT ? "global default" : "declared";
            return "store " + r.storeId() + " (" + source + ")";
        }
        return "parent folder";
    }

    private static String commitAdvice(RootResolution r) {
        return r.storeId() != null
                ? "Commit changes from the store's own repository."
                : "Commit changes from that folder's own repository.";
    }
}
