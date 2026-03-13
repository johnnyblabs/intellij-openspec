package com.johnnyb.openspec.actions;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyb.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Opens IntelliJ's diff viewer showing a delta spec (right) against its
 * corresponding main spec (left).
 */
public final class DeltaSpecDiffAction extends AnAction {

    private final String deltaSpecPath;

    public DeltaSpecDiffAction(String deltaSpecPath) {
        super("Preview Diff");
        this.deltaSpecPath = deltaSpecPath;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        VirtualFile deltaFile = LocalFileSystem.getInstance().findFileByPath(deltaSpecPath);
        if (deltaFile == null) return;

        // Derive domain name from delta spec path: .../specs/<domain>/spec.md
        String domain = OpenSpecFileUtil.getDomainName(deltaFile);
        if (domain == null) return;

        // Find corresponding main spec
        VirtualFile specsDir = OpenSpecFileUtil.getSpecsDir(project);
        VirtualFile mainSpecFile = null;
        if (specsDir != null) {
            VirtualFile domainDir = specsDir.findChild(domain);
            if (domainDir != null) {
                mainSpecFile = domainDir.findChild("spec.md");
            }
        }

        DiffContentFactory contentFactory = DiffContentFactory.getInstance();

        DiffContent leftContent;
        String leftTitle;
        if (mainSpecFile != null) {
            leftContent = contentFactory.create(project, mainSpecFile);
            leftTitle = "specs/" + domain + "/spec.md (current)";
        } else {
            leftContent = contentFactory.create(project, "", deltaFile.getFileType());
            leftTitle = "New capability — no existing spec";
        }

        DiffContent rightContent = contentFactory.create(project, deltaFile);
        String rightTitle = "delta: " + domain + "/spec.md";

        SimpleDiffRequest request = new SimpleDiffRequest(
                "Delta Spec: " + domain,
                leftContent,
                rightContent,
                leftTitle,
                rightTitle
        );

        DiffManager.getInstance().showDiff(project, request);
    }
}
