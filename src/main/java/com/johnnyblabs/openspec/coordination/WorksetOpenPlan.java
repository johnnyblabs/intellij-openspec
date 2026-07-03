package com.johnnyblabs.openspec.coordination;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure planning for the {@code workset open} flow. Maps a workset's members onto the ordered list
 * of folder paths the IDE should open — the first member is the primary (opened in the current
 * window), the rest are offered as attached directories / additional projects. This is deliberately
 * side-effect free and platform-free so the ordering and the "opens N folders" count can be unit
 * tested without a running IDE; the actual multi-folder open (VFS + attach) lives in the panel.
 *
 * <p>The plan never relies on the CLI's {@code workset open}/{@code --code-workspace} flag — it uses
 * only the member folder paths already resolved from {@code workset list}.
 */
public final class WorksetOpenPlan {

    private WorksetOpenPlan() {
    }

    /**
     * The workset's member folder paths in order, first = primary. Blank/null paths are dropped so
     * the caller never tries to open a phantom folder.
     */
    public static List<String> orderedPaths(WorksetEntry workset) {
        List<String> paths = new ArrayList<>();
        if (workset != null) {
            for (WorksetEntry.Member m : workset.members()) {
                if (m.path() != null && !m.path().isBlank()) {
                    paths.add(m.path());
                }
            }
        }
        return paths;
    }

    /** The number of folders the open action will touch — drives the "opens N folders" confirmation. */
    public static int folderCount(WorksetEntry workset) {
        return orderedPaths(workset).size();
    }
}
