package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The tool list "Install into this project" opens, ticked as the CLI's own picker ticks it for a project with no
 * configured tools: each tool found in the project, and each tool installed on this machine (its marker exists; tools
 * the installer treats as universal have none). The preferred AI tool is ticked too, since the user chose it here.
 * Ticked tools come first, then the rest, each in registry order, as upstream sorts its picker.
 */
public final class InstallToolTicks {

    /** One row of the list. */
    public record Row(AiTool tool, boolean ticked) {
    }

    private InstallToolTicks() {
    }

    /** The rows for {@code project}. Reads the disk: never on the EDT. */
    public static List<Row> rows(Path project, @Nullable Path home, @Nullable String xdgConfigHome,
                                 @Nullable String preferredToolName) {
        Set<String> ticked = new LinkedHashSet<>();
        for (AiTool tool : AiToolDetector.detect(project, null, DetectionRules.fromDisk(project))) ticked.add(tool.id());
        ticked.addAll(MachineToolMarkers.installed(home, xdgConfigHome));
        AiTool preferred = AiToolRegistry.byName(preferredToolName);
        if (preferred != null) ticked.add(preferred.id());

        List<Row> first = new ArrayList<>();
        List<Row> rest = new ArrayList<>();
        for (AiTool tool : AiToolRegistry.all()) {
            if (tool.cliId() == null || tool.skillsDir() == null && tool.globalSkillsDir() == null) continue;
            (ticked.contains(tool.id()) ? first : rest).add(new Row(tool, ticked.contains(tool.id())));
        }
        first.addAll(rest);
        return first;
    }

    /** The {@code --tools} value for the ticked rows, in registry order. */
    public static String toolsArg(List<AiTool> ticked) {
        List<String> ids = new ArrayList<>();
        for (AiTool tool : AiToolRegistry.all()) {
            if (ticked.contains(tool)) ids.add(tool.cliId());
        }
        return String.join(",", ids);
    }
}
