package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.util.CliVersion;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * When a project should be told, once, that {@code openspec update} moves it to OpenSpec 2: the detected CLI is 2.0.0
 * or later, and the project still holds a skill copy a 1.x release generated, unedited, not reached through a link and
 * alone in its folder (apart from OS junk), in a tool folder other than {@code .agents}.
 * <p>
 * Released files rather than the shared-skills marker decide it: 1.x already writes the marker for tools rooted at
 * {@code .agents}, any 2.x update removes the released copies, and the edited copies 2.0.0 keeps are not released, so
 * the notice can't come back after a migration. Checked against the captured projects before and after each 2.x update
 * ({@code fixtures/cli/<version>/update-migration/}). Reads the disk: call off the UI thread.
 */
public final class MigrationNotice {

    /** Records, per project, that the notice was shown. */
    public static final String SHOWN_KEY = "openspec.migrationNotice.shown";
    public static final String TITLE = "OpenSpec 2";
    public static final String MESSAGE = "This project was set up by OpenSpec 1.x. Run Update to migrate it to OpenSpec 2.";

    private MigrationNotice() {
    }

    /** Whether the project at {@code root}, with CLI {@code cliVersion} detected, should get the notice. */
    public static boolean applies(Path root, @Nullable String cliVersion) {
        if (cliVersion == null || !CliVersion.atLeast(cliVersion, "2.0.0")) return false;
        for (String folder : toolFolders()) {
            if (holdsReleasedCopy(root.resolve(folder).resolve("skills"))) return true;
        }
        return false;
    }

    /** Every folder a 1.x release wrote a tool's skills into, now or before it moved, except the shared {@code .agents}. */
    static Set<String> toolFolders() {
        Set<String> folders = new LinkedHashSet<>();
        for (AiToolRegistry.AiTool tool : AiToolRegistry.all()) {
            if (tool.skillsDir() != null) folders.add(tool.skillsDir());
            folders.addAll(tool.legacySkillsDirs());
            String former = UpdateMigration.FORMER_ROOTS.get(tool.id());
            if (former != null) folders.add(former);
        }
        folders.remove(".agents");
        return folders;
    }

    /**
     * Whether a skill folder holds anything besides {@code SKILL.md} and operating-system junk. Upstream then calls the
     * folder the user's ({@code copy-beside-notes}), and 2.0.0 keeps it whole, so it says nothing about a 1.x layout.
     */
    private static boolean holdsOtherFiles(Path skill) {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(skill)) {
            for (Path f : files) {
                String name = f.getFileName().toString();
                if (!name.equals("SKILL.md") && !SkillSpot.isOsJunk(name)) return true;
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private static boolean holdsReleasedCopy(Path skills) {
        if (!Files.isDirectory(skills, LinkOption.NOFOLLOW_LINKS)) return false;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(skills, "openspec-*")) {
            for (Path skill : entries) {
                if (Files.isSymbolicLink(skill)) continue;
                Path file = skill.resolve("SKILL.md");
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || holdsOtherFiles(skill)) continue;
                try {
                    if (ReleasedSkillFingerprints.isReleased(Files.readString(file))) return true;
                } catch (IOException | RuntimeException ignored) {
                    // An unreadable copy says nothing about the layout; look at the others.
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}
