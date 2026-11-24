package com.johnnyblabs.openspec.services;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Whether a file event is about a folder a tool can be detected from: a top-level tool path
 * ({@link AiToolRegistry#topLevelPaths}), seen from the project, or under the real location of {@code .agents} when
 * that folder is itself a link (a file watcher may report the target's path). Pure, so plain tests cover it; the real
 * path is resolved by {@link #realAgents}, off the EDT.
 */
public final class ToolFolderEvents {

    private ToolFolderEvents() {
    }

    /**
     * @param path          the event's path as the IDE reports it
     * @param canonicalPath its canonical path, or {@code null}
     * @param realAgents    the real path of {@code <base>/.agents}, or {@code null} when it isn't a link or doesn't exist
     */
    public static boolean matches(String basePath, String path, @Nullable String canonicalPath, @Nullable String realAgents) {
        for (String root : AiToolRegistry.topLevelPaths()) {
            String dir = basePath + "/" + root;
            if (path.equals(dir) || path.startsWith(dir + "/")) return true;
        }
        if (realAgents == null) return false;
        for (String p : new String[] {path, canonicalPath}) {
            if (p != null && (p.equals(realAgents) || p.startsWith(realAgents + "/"))) return true;
        }
        return false;
    }

    /** The real path of {@code <base>/.agents} with {@code /} separators when it differs from the path, else {@code null}. */
    public static @Nullable String realAgents(Path base) {
        Path agents = base.resolve(".agents");
        try {
            Path real = agents.toRealPath();
            if (real.equals(agents.toAbsolutePath().normalize())) return null;
            return real.toString().replace('\\', '/');
        } catch (IOException e) {
            return null;
        }
    }
}
