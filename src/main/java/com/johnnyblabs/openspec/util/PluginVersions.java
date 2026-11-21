package com.johnnyblabs.openspec.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The versions the Settings About line shows: this plugin's version, and the OpenSpec CLI version the build targets.
 * The build writes both into a classpath resource from its own sources ({@code build.gradle.kts} and
 * {@code gradle.properties}), so they can't drift from what was built. Each is {@code "unknown"} when the resource
 * is missing, such as a run without the build's resource step.
 */
public final class PluginVersions {

    static final String BUILD_RESOURCE = "/openspec-target.properties";
    static final String TARGET_KEY = "openspecTargetVersion";
    static final String PLUGIN_KEY = "pluginVersion";
    static final String UNKNOWN = "unknown";

    private PluginVersions() {
    }

    /** This plugin's version, from the build that produced the running plugin. */
    public static String pluginVersion() {
        return read(PLUGIN_KEY);
    }

    /** The OpenSpec CLI version this build targets, from {@code gradle.properties}. */
    public static String targetCliVersion() {
        return read(TARGET_KEY);
    }

    private static String read(String key) {
        try (InputStream in = PluginVersions.class.getResourceAsStream(BUILD_RESOURCE)) {
            if (in == null) return UNKNOWN;
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty(key, UNKNOWN);
        } catch (IOException e) {
            return UNKNOWN;
        }
    }
}
