package com.johnnyblabs.openspec.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * The skills scope a user has saved for {@code openspec init} (CLI 2.1+), read as upstream's {@code resolveSkillScope}
 * reads it ({@code dist/core/init.js}): the {@code OPENSPEC_SKILLS_SCOPE} environment variable, else {@code skillsScope}
 * in the CLI's global {@code config.json}. The project's {@code openspec/config.yaml} isn't read here: {@code init}
 * doesn't.
 */
public final class SavedSkillsScope {

    private SavedSkillsScope() {
    }

    /** {@code "project"}, {@code "global"}, or {@code null} when none is saved. Reads a file: off the UI thread. */
    public static @Nullable String read(Function<String, String> env, Path globalConfig) {
        String fromEnv = env.apply("OPENSPEC_SKILLS_SCOPE");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv.trim();
        try {
            if (!Files.isRegularFile(globalConfig)) return null;
            JsonElement root = JsonParser.parseString(Files.readString(globalConfig, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return null;
            JsonElement scope = root.getAsJsonObject().get("skillsScope");
            return scope != null && scope.isJsonPrimitive() ? scope.getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The saved scope for this process: its environment and the CLI's global config file. */
    public static @Nullable String current() {
        return read(System::getenv, com.johnnyblabs.openspec.root.GlobalConfigPaths.configFile());
    }
}
