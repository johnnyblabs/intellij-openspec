package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every invocation the plugin can make, on every captured CLI release. Each {@link CliCommands} builder is called with
 * sampled parameters (every boolean both ways, strings as {@code "demo"} and {@code ""} and, where nullable,
 * {@code null}, lists empty and with one item) and resolved against each release.
 * <p>
 * On a release inside the builder's {@link OfferedOn} range, where the plugin offers the action, the invocation must
 * resolve, keep every flag it asked for, and emit only a command and flags that release's captured {@code --help}
 * lists. Help is read through {@link CliHelp}, never through {@link CliFlags}, so a mistake in the table can't hide
 * here. This is the test that fails when a newer-only flag would reach an older CLI.
 */
class CliArgsVersionMatrixTest {

    /** Optional flags a builder may drop inside its range, as {@code builder flag}. None yet. */
    static final Set<String> ALLOWED_DROPS = Set.of();

    static final Path SOURCE = Path.of("src/main/java/com/johnnyblabs/openspec/util/CliCommands.java");

    record Builder(Method method, OfferedOn offered, List<Object[]> samples) {
        String name() {
            return method.getName();
        }

        boolean offeredOn(String version) {
            return FixtureVersions.compare(version, offered.since()) >= 0
                    && (offered.until().isEmpty() || FixtureVersions.compare(version, offered.until()) < 0);
        }
    }

    static List<Builder> builders() throws IOException {
        Map<String, Set<Integer>> nullable = nullableParameters(Files.readString(SOURCE));
        List<Builder> out = new ArrayList<>();
        for (Method m : CliCommands.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.getReturnType() != CliArgs.class) continue;
            OfferedOn offered = m.getAnnotation(OfferedOn.class);
            assertNotNull(offered, m.getName() + " has no @OfferedOn");
            out.add(new Builder(m, offered, samples(m, nullable.getOrDefault(m.getName(), Set.of()))));
        }
        out.sort(Comparator.comparing(Builder::name));
        return out;
    }

    /** Which parameter positions are {@code @Nullable}, read from the source (the annotation isn't kept at runtime). */
    static Map<String, Set<Integer>> nullableParameters(String source) {
        Map<String, Set<Integer>> out = new HashMap<>();
        Matcher m = Pattern.compile("public static CliArgs (\\w+)\\(([^)]*)\\)").matcher(source);
        while (m.find()) {
            Set<Integer> positions = new HashSet<>();
            String params = m.group(2).trim();
            if (!params.isEmpty()) {
                String[] parts = params.split(",(?![^<]*>)");
                for (int i = 0; i < parts.length; i++) if (parts[i].contains("@Nullable")) positions.add(i);
            }
            if (out.put(m.group(1), positions) != null) fail("builder names must be unique: " + m.group(1));
        }
        return out;
    }

    /** Every combination of sampled values for {@code m}'s parameters. */
    static List<Object[]> samples(Method m, Set<Integer> nullable) {
        List<Object[]> combos = new ArrayList<>();
        combos.add(new Object[0]);
        Type[] types = m.getGenericParameterTypes();
        for (int i = 0; i < types.length; i++) {
            List<Object> values = valuesFor(m, types[i], nullable.contains(i));
            List<Object[]> next = new ArrayList<>();
            for (Object[] prefix : combos) {
                for (Object v : values) {
                    Object[] c = Arrays.copyOf(prefix, prefix.length + 1);
                    c[prefix.length] = v;
                    next.add(c);
                }
            }
            combos = next;
        }
        return combos;
    }

    private static List<Object> valuesFor(Method m, Type type, boolean nullable) {
        if (type == String.class) {
            List<Object> v = new ArrayList<>(List.of("demo", ""));
            if (nullable) v.add(null);
            return v;
        }
        if (type == boolean.class) return List.of(false, true);
        if (type instanceof ParameterizedType p && p.getRawType() == List.class
                && p.getActualTypeArguments()[0] == String.class) {
            return List.of(List.of(), List.of("demo"));
        }
        throw new AssertionError(m.getName() + ": parameter type " + type + " can't be sampled");
    }

    static CliArgs build(Builder b, Object[] sample) {
        try {
            return (CliArgs) b.method().invoke(null, sample);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError(b.name() + Arrays.toString(sample) + " threw", e);
        }
    }

    @TestFactory
    Stream<DynamicTest> everyInvocationFitsEveryReleaseItIsOfferedOn() throws IOException {
        List<Builder> builders = builders();
        return CliFlagTableContractTest.releases().stream().map(v -> DynamicTest.dynamicTest(v, () -> {
            CliHelp help = CliHelp.of(v);
            List<String> wrong = new ArrayList<>();
            for (Builder b : builders) {
                if (!b.offeredOn(v)) continue;
                for (Object[] sample : b.samples()) {
                    wrong.addAll(problems(b, sample, help));
                }
            }
            assertEquals(List.of(), wrong, v + ": invocations the plugin offers that this release can't run");
        }));
    }

    static List<String> problems(Builder b, Object[] sample, CliHelp help) {
        String v = help.version();
        String call = b.name() + Arrays.toString(sample);
        CliArgs args = build(b, sample);
        CliArgs.Resolved resolved;
        try {
            resolved = args.resolve(v);
        } catch (UnsupportedCliArgumentException e) {
            return List.of(call + ": refused inside its offered range: " + e.getMessage());
        }
        List<String> wrong = new ArrayList<>();
        for (String dropped : resolved.dropped()) {
            if (!ALLOWED_DROPS.contains(b.name() + " " + dropped)) wrong.add(call + ": dropped " + dropped);
        }
        if (!help.hasCommand(args.command())) {
            wrong.add(call + ": openspec " + args.command() + " is not in " + v + "'s help");
            return wrong;
        }
        int allowed = help.maxPositionals(args.command());
        if (args.positionals() > allowed) {
            wrong.add(call + ": " + args.positionals() + " positional arguments; " + v + "'s usage takes " + allowed);
        }
        for (String token : resolved.argv()) {
            if (token.startsWith("--") && !help.hasFlag(args.command(), token)) {
                wrong.add(call + ": openspec " + args.command() + " " + token + " is not in " + v + "'s help");
            }
        }
        return wrong;
    }

    @Test
    void everyBuilderIsOfferedOnAtLeastOneCapturedRelease() throws IOException {
        List<String> releases = CliFlagTableContractTest.releases();
        List<String> never = new ArrayList<>();
        for (Builder b : builders()) {
            if (releases.stream().noneMatch(b::offeredOn)) never.add(b.name());
        }
        assertEquals(List.of(), never, "builders the matrix never exercises");
        assertTrue(builders().size() >= 35, "sanity: the catalog is enumerated");
    }

    @Test
    void theSamplerVisitsEveryBranch() throws IOException {
        Map<String, Builder> byName = new HashMap<>();
        for (Builder b : builders()) byName.put(b.name(), b);
        // Strings sample two values (three if nullable), booleans two, lists two.
        assertEquals(4, byName.get("archive").samples().size(), "archive: 2 strings x 2 booleans");
        assertEquals(6, byName.get("newChange").samples().size(), "newChange: 2 names x 3 schemas");
        assertEquals(12, byName.get("schemaInit").samples().size(), "schemaInit: 2 x 3 x 2");
        assertTrue(byName.get("newChange").samples().stream().anyMatch(s -> s[1] == null));
    }
}
