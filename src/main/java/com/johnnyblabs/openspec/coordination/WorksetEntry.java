package com.johnnyblabs.openspec.coordination;

import java.util.List;

/**
 * A local OpenSpec 1.5 workset — a purely local, composed working view over one or more
 * stores. Derived from {@code openspec workset list --json} (entry shape
 * {@code {name, members: [{name, path}]}}) or the on-disk {@code worksets/worksets.yaml} in
 * the fallback path.
 *
 * @param name    the workset name
 * @param members the workset's member folders (never null; defensively copied)
 */
public record WorksetEntry(String name, List<Member> members) {

    public WorksetEntry {
        members = members != null ? List.copyOf(members) : List.of();
    }

    /**
     * One member folder of a workset.
     *
     * @param name the member's display name
     * @param path the member's folder path
     */
    public record Member(String name, String path) {
    }
}
