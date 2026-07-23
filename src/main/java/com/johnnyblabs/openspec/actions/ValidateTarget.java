package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;

/**
 * The item a validate invocation is scoped to.
 *
 * <p>{@link Kind#WHOLE_PROJECT} reproduces the classic "validate everything"
 * behavior; {@link Kind#SPEC} / {@link Kind#CHANGE} scope validation to a single
 * capability spec or an active change (the {@code id} is the directory name, which
 * is exactly the argument the CLI expects — never a file path).</p>
 *
 * <p>{@link #resolveTarget} maps a Project-View selection to a target purely from
 * the on-disk path structure, so the CLI {@code --type} is always known and an
 * <em>archived</em> change name is never forwarded as {@code --type change} (which
 * the CLI rejects with a misleading "No deltas found" error).</p>
 */
public record ValidateTarget(Kind kind, String id) {

    public enum Kind {SPEC, CHANGE, WHOLE_PROJECT}

    public static ValidateTarget wholeProject() {
        return new ValidateTarget(Kind.WHOLE_PROJECT, null);
    }

    public static ValidateTarget spec(String id) {
        return new ValidateTarget(Kind.SPEC, id);
    }

    public static ValidateTarget change(String id) {
        return new ValidateTarget(Kind.CHANGE, id);
    }

    public boolean isWholeProject() {
        return kind == Kind.WHOLE_PROJECT;
    }

    /** The CLI {@code --type} value for a single-item validate, or {@code null} for whole-project. */
    public String cliType() {
        return switch (kind) {
            case SPEC -> "spec";
            case CHANGE -> "change";
            case WHOLE_PROJECT -> null;
        };
    }

    /**
     * Resolves a Project-View selection to a scoped validate target, purely from the
     * path structure under {@code openspec/}:
     * <ul>
     *   <li>{@code openspec/specs/<cap>/**} → {@link #spec(String) SPEC(cap)}</li>
     *   <li>active {@code openspec/changes/<name>/**} → {@link #change(String) CHANGE(name)}</li>
     *   <li>{@code openspec/changes/archive/**}, the {@code openspec/} root, {@code config.yaml},
     *       any other non-item file, an empty/null selection, or a selection spanning multiple
     *       distinct items → {@link #wholeProject() WHOLE_PROJECT}</li>
     * </ul>
     * An archived change name is never returned as a CHANGE target.
     */
    public static ValidateTarget resolveTarget(VirtualFile[] files, Project project) {
        if (project == null || files == null || files.length == 0) {
            return wholeProject();
        }
        VirtualFile root = OpenSpecFileUtil.getOpenSpecRoot(project);
        if (root == null) {
            return wholeProject();
        }
        String rootPath = root.getPath();

        ValidateTarget resolved = null;
        for (VirtualFile file : files) {
            ValidateTarget t = resolveOne(file, rootPath);
            if (t.isWholeProject()) {
                // A non-item selection (archive/config/root/outside) forces whole-project.
                return wholeProject();
            }
            if (resolved == null) {
                resolved = t;
            } else if (!resolved.equals(t)) {
                // Selection spans two distinct items — validate the whole project.
                return wholeProject();
            }
        }
        return resolved == null ? wholeProject() : resolved;
    }

    private static ValidateTarget resolveOne(VirtualFile file, String rootPath) {
        if (file == null) {
            return wholeProject();
        }
        String path = file.getPath();
        // The openspec root itself, or anything outside it, is not a single item.
        if (!path.startsWith(rootPath + "/")) {
            return wholeProject();
        }
        String rel = path.substring(rootPath.length() + 1);
        String[] seg = rel.split("/");
        if (seg.length >= 2 && "specs".equals(seg[0]) && !seg[1].isEmpty()) {
            return spec(seg[1]);
        }
        if (seg.length >= 2 && "changes".equals(seg[0]) && !seg[1].isEmpty()) {
            if ("archive".equals(seg[1])) {
                // Archived changes aren't single-validatable — never forward the name.
                return wholeProject();
            }
            return change(seg[1]);
        }
        return wholeProject();
    }
}
