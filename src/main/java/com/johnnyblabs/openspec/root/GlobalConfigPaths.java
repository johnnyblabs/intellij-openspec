package com.johnnyblabs.openspec.root;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * The OpenSpec CLI's global config file, {@code <config-dir>/openspec/config.json}, where {@code defaultStore} lives.
 * The config dir follows upstream's {@code getGlobalConfigDir}: {@code $XDG_CONFIG_HOME} when set on any platform,
 * otherwise {@code %APPDATA%} on Windows (falling back to {@code ~/AppData/Roaming}), otherwise {@code ~/.config}.
 * This is not the data dir ({@code CoordinationPaths}), which uses {@code %LOCALAPPDATA%} on Windows.
 */
public final class GlobalConfigPaths {

    static final String DIR_NAME = "openspec";
    static final String FILE_NAME = "config.json";

    private GlobalConfigPaths() {
    }

    public static Path configFile() {
        return configFile(System::getenv, SystemInfo.isWindows, System.getProperty("user.home"));
    }

    public static Path configFile(Function<String, String> env, boolean windows, @Nullable String home) {
        String resolvedHome = home != null ? home : ".";
        String xdg = env.apply("XDG_CONFIG_HOME");
        Path dir;
        if (xdg != null && !xdg.isBlank()) {
            dir = Path.of(xdg, DIR_NAME);
        } else if (windows) {
            String appData = env.apply("APPDATA");
            dir = appData != null && !appData.isBlank()
                    ? Path.of(appData, DIR_NAME)
                    : Path.of(resolvedHome, "AppData", "Roaming", DIR_NAME);
        } else {
            dir = Path.of(resolvedHome, ".config", DIR_NAME);
        }
        return dir.resolve(FILE_NAME);
    }
}
