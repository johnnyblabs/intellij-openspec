package com.johnnyblabs.openspec.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What a skill's spot in a tool's own folder holds, without following links: a port of upstream's
 * {@code classifySkillLinkSpot} ({@code dist/core/shared/skill-links.js}), narrowed to what detection and lookup need.
 * Contract-tested against upstream's verdicts on captured shapes ({@code fixtures/cli/<version>/skill-spots/}).
 * <p>
 * Upstream's verdicts map onto these shapes as follows:
 * <ul>
 *   <li>{@code link}: {@link Shape#LINK}, or {@link Shape#BROKEN} when the shared folder it points to is gone. Upstream
 *       compares paths only, so a broken link is still a link and the tool stays set up; the split only tells lookup
 *       there is nothing to read through it.</li>
 *   <li>{@code released} for a regular file (what git writes for a link without link support):
 *       {@link Shape#RELEASED}.</li>
 *   <li>{@code copy}, {@code released} for a folder, and {@code user} for a folder holding a {@code SKILL.md}:
 *       {@link Shape#COPY}. Detection and lookup only ask whether {@code SKILL.md} exists, so the content check that
 *       tells these apart (what {@code update} may replace) changes nothing here.</li>
 *   <li>any other {@code user}: {@link Shape#USER}; {@code missing}: {@link Shape#MISSING}.</li>
 * </ul>
 * Pure nio: one attribute read per spot, plus a link's real path for a link or link text.
 */
public final class SkillSpot {

    public enum Shape {
        /** A symbolic link or junction pointing to the shared skill folder, which exists. */
        LINK,
        /** A link pointing to the shared skill folder, which no longer exists. */
        BROKEN,
        /** A small regular file holding the relative path of the shared skill folder (a link git checked out as text). */
        RELEASED,
        /** A folder (not a link) whose {@code SKILL.md} exists. */
        COPY,
        /** Anything else that is there: a link or link text pointing elsewhere, or a folder without {@code SKILL.md}. */
        USER,
        /** Nothing there, or an empty folder (operating-system junk files don't count). */
        MISSING
    }

    /** Upstream's {@code OS_JUNK}; {@link #isOsJunk} adds every name starting with {@code ._}. */
    private static final Set<String> OS_JUNK = Set.of(".DS_Store", "Thumbs.db", "desktop.ini");

    /** Upstream reads a checked-out link only up to this size. */
    private static final int MAX_LINK_TEXT = 1024;

    private SkillSpot() {
    }

    /** Upstream's {@code isOsJunk}. */
    static boolean isOsJunk(String name) {
        return OS_JUNK.contains(name) || name.startsWith("._");
    }

    /** The shape of {@code spot}, the skill's spot in a tool's own folder, whose shared copy is {@code target}. */
    public static Shape classify(Path spot, Path target) {
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(spot, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            return Shape.MISSING;
        }
        // isOther() is how nio reports a Windows junction; upstream never makes one, but a user or another tool may.
        if (attrs.isSymbolicLink() || attrs.isOther()) {
            String text = linkText(spot);
            boolean pointsTo = text != null ? pointsTo(spot, text, target) : sameRealPath(spot, target);
            if (!pointsTo) return Shape.USER;
            return Files.isDirectory(target) ? Shape.LINK : Shape.BROKEN;
        }
        if (attrs.isRegularFile()) {
            return isCheckedOutLink(spot, attrs.size(), target) ? Shape.RELEASED : Shape.USER;
        }
        if (attrs.isDirectory()) {
            boolean anything;
            try (Stream<Path> entries = Files.list(spot)) {
                anything = entries.anyMatch(p -> !isOsJunk(p.getFileName().toString()));
            } catch (IOException e) {
                return Shape.USER;
            }
            if (!anything) return Shape.MISSING;
            return Files.isRegularFile(spot.resolve("SKILL.md")) ? Shape.COPY : Shape.USER;
        }
        return Shape.USER;
    }

    /** Whether the shape means OpenSpec set the spot up: upstream counts a link (even broken) and link text. */
    public static boolean isOpenSpecLink(Shape shape) {
        return shape == Shape.LINK || shape == Shape.BROKEN || shape == Shape.RELEASED;
    }

    private static String linkText(Path spot) {
        try {
            return Files.readSymbolicLink(spot).toString();
        } catch (IOException | UnsupportedOperationException e) {
            return null;
        }
    }

    /** Upstream's {@code isCheckedOutLink}: the file's whole text, one line, is a path to {@code target}. */
    private static boolean isCheckedOutLink(Path file, long size, Path target) {
        if (size > MAX_LINK_TEXT) return false;
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            return false;
        }
        if (text.isEmpty() || text.contains("\n") || text.contains("\r")) return false;
        return pointsTo(file, text, target);
    }

    /**
     * Upstream's {@code linkPointsTo}: {@code linked} (separators of either kind) resolved from the spot's folder is
     * {@code target}, compared literally, then through real paths (macOS {@code /var} is {@code /private/var}; a project
     * opened through a link), then as the real paths of the spot and the target.
     */
    static boolean pointsTo(Path spot, String linked, Path target) {
        String normalized = linked.replace('\\', '/');
        Path parent = spot.toAbsolutePath().getParent();
        try {
            if (parent.resolve(normalized).normalize().equals(target.toAbsolutePath().normalize())) return true;
            Path targetAbs = target.toAbsolutePath();
            Path viaReal = realOrSelf(parent).resolve(normalized).normalize();
            if (viaReal.equals(realOrSelf(targetAbs.getParent()).resolve(targetAbs.getFileName()).normalize())) return true;
        } catch (InvalidPathException e) {
            return false;
        }
        return sameRealPath(spot, target);
    }

    private static boolean sameRealPath(Path a, Path b) {
        try {
            return a.toRealPath().equals(b.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static Path realOrSelf(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            return p.toAbsolutePath().normalize();
        }
    }
}
