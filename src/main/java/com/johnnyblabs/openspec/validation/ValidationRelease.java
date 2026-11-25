package com.johnnyblabs.openspec.validation;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.util.CliVersion;
import com.johnnyblabs.openspec.util.PluginVersions;
import org.jetbrains.annotations.Nullable;

/**
 * The OpenSpec CLI release whose rules the built-in validator follows: the detected CLI's version, or the plugin's
 * target release when no CLI is detected. A rule a release introduced applies only from that release
 * ({@link #has}); a detected version that can't be read as a release number satisfies no rule, the laxest outcome.
 */
public final class ValidationRelease {

    /** Long requirements are a warning (strict-failing) from here: main specs and ADDED deltas. */
    public static final String TOO_LONG = "1.14.1";
    /** A scenario needs a trigger and an outcome from here. */
    public static final String SCENARIO_STEPS = "1.16.0";
    /** A MODIFIED requirement that grows past the limit, and delta front matter, from here. */
    public static final String MODIFIED_GROWTH = "2.1.1";
    public static final String DELTA_FRONT_MATTER = "2.1.1";
    /**
     * A new main spec's layout and the delta Purpose reader, from here: a blank line after {@code ## Purpose}, a
     * byte-order mark dropped, and the delta's front matter read past (left out of the merge, with a warning).
     */
    public static final String NEW_SPEC_LAYOUT = "2.1.1";

    private ValidationRelease() {
    }

    /** The release followed in {@code project}. Cheap: reads the detection snapshot and a cached target. */
    public static @Nullable String followed(Project project) {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection != null && detection.isAvailable()) return detection.getDetectedVersion();
        return Target.VERSION;
    }

    /** The plugin's target release, read once from the build's properties. */
    private static final class Target {
        static final String VERSION = PluginVersions.targetCliVersion();
    }

    /** Whether {@code release} has a rule introduced in {@code since}. */
    public static boolean has(@Nullable String release, String since) {
        return release != null && CliVersion.atLeast(release, since);
    }
}
