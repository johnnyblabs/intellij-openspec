package com.johnnyblabs.openspec.search;

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor;
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.Processor;
import com.johnnyblabs.openspec.model.SpecFile;
import com.johnnyblabs.openspec.services.SpecParsingService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Search Everywhere contributor over OpenSpec requirement content. Re-homes the reach the Browse
 * tree's content-search field used to provide (requirement bodies + scenario text, not just labels)
 * — see the restructure that dropped the always-on Specs tree. Built on the long-stable legacy
 * {@code WeightedSearchEverywhereContributor} API (viable on the 2024.2 floor); intentionally does
 * NOT touch the 2025.12+ Search Everywhere API, which is outside the plugin's support range.
 *
 * <p>{@link #fetchWeightedElements} is invoked by the platform on a background thread with a
 * {@link ProgressIndicator}; it reuses {@link SpecParsingService#parseAllSpecs()} (a pure VFS +
 * line-scan read, no platform index) inside a read action, then the headless-testable
 * {@link SpecRequirementSearch}. Because it needs no index it is {@link DumbAware} — content search
 * works during indexing.
 */
public class SpecSearchEverywhereContributor
        implements WeightedSearchEverywhereContributor<SpecRequirementMatch>, DumbAware {

    private final Project project;

    public SpecSearchEverywhereContributor(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    @Override
    public String getSearchProviderId() {
        return SpecSearchEverywhereContributor.class.getSimpleName();
    }

    @NotNull
    @Override
    public String getGroupName() {
        return "OpenSpec Specs";
    }

    @Override
    public int getSortWeight() {
        return 1000;
    }

    @Override
    public boolean showInFindResults() {
        return false;
    }

    @Override
    public void fetchWeightedElements(@NotNull String pattern,
                                      @NotNull ProgressIndicator progressIndicator,
                                      @NotNull Processor<? super FoundItemDescriptor<SpecRequirementMatch>> consumer) {
        SpecParsingService parsing = project.getService(SpecParsingService.class);
        if (parsing == null) {
            return;
        }
        List<SpecFile> specs = ApplicationManager.getApplication().runReadAction(
                (ThrowableComputable<List<SpecFile>, RuntimeException>) parsing::parseAllSpecs);
        for (SpecRequirementMatch match : SpecRequirementSearch.find(specs, pattern)) {
            progressIndicator.checkCanceled();
            if (!consumer.process(new FoundItemDescriptor<>(match, 1))) {
                return;
            }
        }
    }

    @Override
    public boolean processSelectedItem(@NotNull SpecRequirementMatch selected, int modifiers, @NotNull String searchText) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(selected.filePath());
        if (file == null) {
            return false;
        }
        int line = 0;
        try {
            String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            line = RequirementLineFinder.findRequirementLine(content, selected.requirement().getName());
        } catch (Exception ignored) {
            // fall through to the top of the file
        }
        new OpenFileDescriptor(project, file, line, 0).navigate(true);
        return true;
    }

    @NotNull
    @Override
    public ListCellRenderer<? super SpecRequirementMatch> getElementsRenderer() {
        return new SimpleListCellRenderer<SpecRequirementMatch>() {
            @Override
            public void customize(@NotNull JList<? extends SpecRequirementMatch> list,
                                  SpecRequirementMatch value, int index, boolean selected, boolean hasFocus) {
                if (value != null) {
                    setText(value.presentableText());
                    setToolTipText(value.locationText());
                }
            }
        };
    }

    @Nullable
    @Override
    public Object getDataForItem(@NotNull SpecRequirementMatch element, @NotNull String dataId) {
        return null;
    }

    @Override
    public void dispose() {
        // no resources held
    }
}
