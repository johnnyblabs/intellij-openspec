package com.johnnyblabs.openspec.root;

import com.johnnyblabs.openspec.CliFixtureSuite;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.coordination.CoordinationPaths;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Replays every captured {@code openspec context --json} run (scripts/capture-store-root.py) against
 * {@link RootResolver}: the world is rebuilt in a temp directory with the registry and global config the CLI wrote
 * for that scenario's phase, and the resolver must report the same root path, source, store id and problem code,
 * judged by the captured release's rules.
 */
@CliFixtureSuite
class StoreRootContractTest {

    private static final String WORLD = "store-root/world.json";
    /** CLI errors that mean "not set up" to the plugin, which offers init instead of showing a problem. */
    private static final List<String> NOT_SET_UP = List.of("no_openspec_root", "no_root_with_registered_stores");

    static List<String> versions() {
        return FixtureVersions.versionsWith(WORLD);
    }

    static Stream<Object[]> scenarios() throws IOException {
        List<Object[]> out = new ArrayList<>();
        for (String version : versions()) {
            JsonArray scenarios = json(FixtureVersions.CLI.resolve(version).resolve("store-root/scenarios.json"))
                    .getAsJsonArray();
            for (JsonElement s : scenarios) {
                out.add(new Object[]{version, s.getAsJsonObject().get("name").getAsString(), s.getAsJsonObject()});
            }
        }
        return out.stream();
    }

    @Test
    void everyReleaseTheResolverGatesOnIsCaptured() {
        List<String> captured = versions();
        for (String required : List.of("1.5.0", "1.7.0", "2.1.1", "2.2.0")) {
            assertTrue(captured.contains(required), "missing store-root capture for " + required + ": " + captured);
        }
    }

    @Test
    void eachCaptureIsFromTheReleaseItIsFiledUnder() throws IOException {
        for (String version : versions()) {
            JsonObject world = json(FixtureVersions.CLI.resolve(version).resolve(WORLD)).getAsJsonObject();
            assertEquals(version, world.get("cli_version").getAsString(), "openspec --version of the " + version + " capture");
            assertTrue(world.get("integrity").getAsString().startsWith("sha512-"), version);
        }
    }

    @Test
    void theReleaseBeforeTheDefaultStoreReallyHadOneSet() throws IOException {
        // Without this the 1.5.0 "default store ignored" scenario would pass even if 1.5.0 honored the key.
        JsonObject world = json(FixtureVersions.CLI.resolve("1.5.0").resolve(WORLD)).getAsJsonObject();
        String config = world.getAsJsonObject("phases").getAsJsonObject("default").get("global_config").getAsString();
        assertTrue(config.contains("\"defaultStore\": \"demo\""), config);
    }

    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("scenarios")
    void resolvesAsTheCliDid(String version, String name, JsonObject scenario, @TempDir Path tmp) throws IOException {
        Path w = tmp.toRealPath();
        JsonObject world = json(FixtureVersions.CLI.resolve(version).resolve(WORLD)).getAsJsonObject();
        build(world, scenario.get("phase").getAsString(), w);
        JsonObject xdg = world.getAsJsonObject("xdg");
        Path dataHome = w.resolve(xdg.get("data").getAsString());
        Path configHome = w.resolve(xdg.get("config").getAsString());
        RootResolver.Inputs inputs = new RootResolver.Inputs(
                GlobalConfigPaths.configFile(Map.of("XDG_CONFIG_HOME", configHome.toString())::get, false, null),
                CoordinationPaths.resolve(Map.of("XDG_DATA_HOME", dataHome.toString())::get, false, null),
                version);

        RootResolution actual = RootResolver.resolve(w.resolve(scenario.get("cwd").getAsString()), inputs);

        JsonObject context = scenario.getAsJsonObject("context");
        JsonElement root = context.get("root");
        if (root != null && root.isJsonObject()) {
            JsonObject r = root.getAsJsonObject();
            assertTrue(actual.resolved(), "expected a root, got " + actual);
            assertEquals(Path.of(r.get("path").getAsString().replace("<W>", w.toString())).toRealPath(),
                    actual.root(), "root path");
            assertNotNull(actual.source());
            assertEquals(r.get("source").getAsString(), actual.source().cliName(), "source");
            assertEquals(r.has("store_id") ? r.get("store_id").getAsString() : null, actual.storeId(), "store id");
            assertNull(actual.problem(), "no problem expected");
        } else {
            String code = context.getAsJsonArray("status").get(0).getAsJsonObject().get("code").getAsString();
            assertFalse(actual.resolved(), "expected no root (" + code + "), got " + actual.root());
            if (NOT_SET_UP.contains(code)) {
                assertNull(actual.problem(), "the CLI's " + code + " is 'not set up' to the plugin");
            } else {
                JsonObject status = context.getAsJsonArray("status").get(0).getAsJsonObject();
                assertNotNull(actual.problem(), "expected problem " + code);
                assertEquals(code, actual.problem().code(), "problem code");
                // The message and fix are what the tool window shows, so they must read as the CLI's.
                String message = norm(status.get("message").getAsString().replace("<W>", w.toString()));
                if (PARSER_DETAIL.contains(code)) {
                    // The detail after the prefix comes from upstream's YAML and schema libraries.
                    String prefix = message.substring(0, message.indexOf(PARSER_PREFIX) + PARSER_PREFIX.length());
                    assertTrue(norm(actual.problem().message()).startsWith(prefix), actual.problem().message());
                } else {
                    assertEquals(message, norm(actual.problem().message()), "problem message");
                }
                JsonElement fix = status.get("fix");
                assertEquals(fix == null || fix.isJsonNull() ? null : norm(fix.getAsString().replace("<W>", w.toString())),
                        actual.problem().fix() == null ? null : norm(actual.problem().fix()), "problem fix");
            }
        }
        String stderr = scenario.get("stderr").getAsString();
        String expectedWarning = stderr.contains("the declaration is ignored") ? norm(stderr.replace("<W>", w.toString())) : null;
        assertEquals(expectedWarning, actual.warning() == null ? null : norm(actual.warning()), "pointer-ignored warning");
    }

    /** Codes whose message ends in a parser's own words; compared up to {@link #PARSER_PREFIX}. */
    private static final List<String> PARSER_DETAIL = List.of("invalid_store_metadata");
    private static final String PARSER_PREFIX = "Invalid store metadata state: ";

    /** Separators as the fixtures write them, so a Windows path compares equal. */
    private static String norm(String s) {
        return s.replace('\\', '/');
    }

    /** Recreates the captured world under {@code w}, with the registry and global config of {@code phase}. */
    static void build(JsonObject world, String phase, Path w) throws IOException {
        for (JsonElement d : world.getAsJsonArray("dirs")) {
            Files.createDirectories(w.resolve(d.getAsString()));
        }
        for (Map.Entry<String, JsonElement> f : world.getAsJsonObject("files").entrySet()) {
            write(w.resolve(f.getKey()), f.getValue().getAsString(), w);
        }
        JsonObject xdg = world.getAsJsonObject("xdg");
        JsonObject p = world.getAsJsonObject("phases").getAsJsonObject(phase);
        Path registry = w.resolve(xdg.get("data").getAsString()).resolve("openspec/stores/registry.yaml");
        Path config = w.resolve(xdg.get("config").getAsString()).resolve("openspec/config.json");
        if (p.get("registry").isJsonPrimitive()) write(registry, p.get("registry").getAsString(), w);
        if (p.get("global_config").isJsonPrimitive()) write(config, p.get("global_config").getAsString(), w);
    }

    private static void write(Path file, String content, Path w) throws IOException {
        Files.createDirectories(file.getParent());
        // Forward slashes keep a Windows path a plain YAML scalar.
        Files.writeString(file, content.replace("<W>", w.toString().replace('\\', '/')));
    }

    private static JsonElement json(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file));
    }
}
