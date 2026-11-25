package com.johnnyblabs.openspec.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Recreates a captured detection layout ({@code detect/<case>/layout.json}) on disk.
 * <p>
 * A layout has a {@code project} and a {@code home} side, each a map of path to content, and an optional top-level
 * {@code links} map ({@code {"project": {path: target}, "home": {...}}}). Links are recorded as data because a real
 * link committed to git checks out differently per platform. A target is relative to the link's folder, or an absolute
 * path under {@code /fixture/demo-project} or {@code /fixture/home}, the names the capture gives the two sides.
 * <p>
 * {@link LinkMode} picks how each link is written, covering the three forms a user's checkout can hold.
 * <p>
 * A content value {@code "@<key>"} names a text stored once in a separate map (the update-migration captures keep each
 * release's file texts in {@code update-migration/texts.json}); pass that map to
 * {@link #materialize(JsonObject, Path, Path, LinkMode, JsonObject)}. Without one, values are written as they are.
 */
public final class FixtureLayout {

    /** How a recorded link is written. */
    public enum LinkMode {
        /** A real symbolic link. The test is skipped where the platform can't create one. */
        SYMLINK,
        /** A real symbolic link where possible, otherwise a copy of the target folder. */
        SYMLINK_OR_COPY,
        /** A copy of the target folder, as a copy install leaves it. */
        COPY,
        /** A file holding the target path, as git leaves a link when {@code core.symlinks} is off (Windows). */
        LINK_TEXT
    }

    static final String FIXTURE_PROJECT = "/fixture/demo-project";
    static final String FIXTURE_HOME = "/fixture/home";

    private FixtureLayout() {
    }

    /** Writes both sides of {@code layout} and then its links. Files come first: a link's target must exist. */
    public static void materialize(JsonObject layout, Path project, Path home, LinkMode mode) throws IOException {
        materialize(layout, project, home, mode, null);
    }

    /** As {@link #materialize(JsonObject, Path, Path, LinkMode)}, resolving {@code "@<key>"} values from {@code texts}. */
    public static void materialize(JsonObject layout, Path project, Path home, LinkMode mode, JsonObject texts)
            throws IOException {
        writeFiles(layout.getAsJsonObject("project"), project, texts);
        writeFiles(layout.getAsJsonObject("home"), home, texts);
        JsonObject links = layout.getAsJsonObject("links");
        if (links == null) return;
        writeLinks(links.getAsJsonObject("project"), project, project, home, mode);
        writeLinks(links.getAsJsonObject("home"), home, project, home, mode);
    }

    /** Writes one side's files under {@code root}. */
    public static void writeFiles(JsonObject files, Path root) throws IOException {
        writeFiles(files, root, null);
    }

    /** Writes one side's files under {@code root}, resolving {@code "@<key>"} values from {@code texts}. */
    public static void writeFiles(JsonObject files, Path root, JsonObject texts) throws IOException {
        Files.createDirectories(root);
        if (files == null) return;
        for (Map.Entry<String, JsonElement> f : files.entrySet()) {
            Path path = root.resolve(f.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, content(f.getValue().getAsString(), texts));
        }
    }

    /** A recorded value as file content: a {@code "@<key>"} reference resolved from {@code texts}, else the value. */
    static String content(String value, JsonObject texts) {
        if (texts == null || !value.startsWith("@")) return value;
        JsonElement text = texts.get(value.substring(1));
        if (text == null) throw new IllegalArgumentException("no stored text for " + value);
        return text.getAsString();
    }

    private static void writeLinks(JsonObject links, Path side, Path project, Path home, LinkMode mode)
            throws IOException {
        if (links == null) return;
        Map<String, String> sorted = new TreeMap<>();
        links.entrySet().forEach(e -> sorted.put(e.getKey(), e.getValue().getAsString()));
        for (Map.Entry<String, String> l : sorted.entrySet()) {
            Path link = side.resolve(l.getKey());
            String target = l.getValue();
            Path resolved = resolve(link, target, project, home);
            Files.createDirectories(link.getParent());
            switch (mode) {
                case SYMLINK -> {
                    try {
                        Files.createSymbolicLink(link, linkValue(link, target, resolved));
                    } catch (IOException | UnsupportedOperationException e) {
                        Assumptions.abort("this platform can't create symbolic links: " + e);
                    }
                }
                case SYMLINK_OR_COPY -> {
                    try {
                        Files.createSymbolicLink(link, linkValue(link, target, resolved));
                    } catch (IOException | UnsupportedOperationException e) {
                        copyTree(resolved, link);
                    }
                }
                case COPY -> copyTree(resolved, link);
                case LINK_TEXT -> Files.writeString(link, target);
            }
        }
    }

    /** The target on disk, refusing one that leaves both sides of the layout. */
    static Path resolve(Path link, String target, Path project, Path home) {
        Path resolved;
        if (target.startsWith(FIXTURE_PROJECT + "/")) {
            resolved = project.resolve(target.substring(FIXTURE_PROJECT.length() + 1));
        } else if (target.startsWith(FIXTURE_HOME + "/")) {
            resolved = home.resolve(target.substring(FIXTURE_HOME.length() + 1));
        } else if (target.startsWith("/")) {
            throw new IllegalArgumentException(link + " -> " + target + ": an absolute target outside the fixture");
        } else {
            resolved = link.getParent().resolve(target);
        }
        resolved = resolved.normalize();
        if (!resolved.startsWith(project.normalize()) && !resolved.startsWith(home.normalize())) {
            throw new IllegalArgumentException(link + " -> " + target + " leaves the layout");
        }
        return resolved;
    }

    /** What a real link holds: the recorded relative target, or a relative path to the remapped absolute one. */
    private static Path linkValue(Path link, String target, Path resolved) {
        return target.startsWith("/") ? link.getParent().relativize(resolved) : Paths.get(target);
    }

    private static void copyTree(Path from, Path to) throws IOException {
        if (!Files.exists(from)) {
            throw new IOException("link target " + from + " is not in the layout");
        }
        if (Files.isRegularFile(from)) {
            Files.copy(from, to);
            return;
        }
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : walk.sorted().toList()) {
                Path dest = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest);
                }
            }
        }
    }
}
