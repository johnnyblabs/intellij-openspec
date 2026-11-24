package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The optional {@code cliMatrix} release gate: every invocation the plugin can make, run against the real CLI package
 * of each swept release. {@link CliArgsVersionMatrixTest} checks the same invocations against captured help; this runs
 * them, so a flag the help lists but the CLI still rejects, or a table mistake that both the table and the matrix test
 * share, fails here.
 * <p>
 * Runs only as {@code ./gradlew cliMatrix}, after {@code scripts/install-cli-matrix.py} has installed the packages into
 * {@code $OSP_CAPTURE_CACHE}. Each release runs in a scratch project under its own HOME and XDG directories, with
 * telemetry and prompts off and stdin closed. Only the CLI's argument errors fail the gate: a command that exits
 * non-zero for a reason of its own (there is no change named {@code demo}) is expected.
 */
@Tag("cli-matrix")
class CliMatrixRealCliTest {

    static final Pattern ARGUMENT_ERROR = Pattern.compile("(?i)unknown option|unknown command|too many arguments");

    /** CLI 2.1.0 and later declare this Node floor. */
    static final int[] NODE_FLOOR_21 = {22, 20, 0};

    static Path cache() {
        String env = System.getenv("OSP_CAPTURE_CACHE");
        return env != null && !env.isBlank() ? Path.of(env) : Path.of(System.getProperty("java.io.tmpdir"), "osp-capture-pkgs");
    }

    static Path bin(String version) {
        return cache().resolve(version).resolve("node_modules/@fission-ai/openspec/bin/openspec.js");
    }

    @TestFactory
    Stream<DynamicTest> everyInvocationRunsOnTheRealCli() throws IOException {
        List<String> releases = CliFlagTableContractTest.releases();
        List<CliArgsVersionMatrixTest.Builder> builders = CliArgsVersionMatrixTest.builders();
        boolean any = releases.stream().anyMatch(v -> Files.isRegularFile(bin(v)));
        Assumptions.assumeTrue(any, "no CLI packages in " + cache() + ": run scripts/install-cli-matrix.py first");
        int[] node = nodeVersion();
        return releases.stream().map(v -> DynamicTest.dynamicTest(v, () -> {
            Assumptions.assumeTrue(Files.isRegularFile(bin(v)), v + " is not installed in " + cache());
            Assumptions.assumeTrue(FixtureVersions.compare(v, "2.1.0") < 0 || atLeast(node, NODE_FLOOR_21),
                    v + " needs Node 22.20 or newer");
            List<String> wrong = runAll(v, builders);
            assertEquals(List.of(), wrong, v + ": the real CLI rejected the plugin's arguments");
        }));
    }

    static List<String> runAll(String version, List<CliArgsVersionMatrixTest.Builder> builders) throws Exception {
        Path root = Files.createTempDirectory("osp-cli-matrix-" + version + "-");
        Path home = Files.createDirectories(root.resolve("home"));
        Path project = Files.createDirectories(root.resolve("project"));
        try {
            Run init = run(version, project, home, List.of("init", "--tools", "none"), Map.of());
            assertEquals(0, init.exit(), version + " init failed:\n" + init.output());
            List<String> wrong = new ArrayList<>();
            for (CliArgsVersionMatrixTest.Builder b : builders) {
                if (!b.offeredOn(version)) continue;
                Set<List<String>> seen = new HashSet<>();
                for (Object[] sample : b.samples()) {
                    CliArgs args = CliArgsVersionMatrixTest.build(b, sample);
                    List<String> argv = args.resolve(version).argv();
                    if (!seen.add(argv)) continue;
                    Run r = run(version, project, home, argv, args.env());
                    if (ARGUMENT_ERROR.matcher(r.output()).find()) {
                        wrong.add("openspec " + String.join(" ", argv) + " -> " + firstLine(r.output()));
                    }
                }
            }
            return wrong;
        } finally {
            deleteTree(root);
        }
    }

    record Run(int exit, String output) {
    }

    static Run run(String version, Path cwd, Path home, List<String> argv, Map<String, String> extraEnv)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("node", bin(version).toString()));
        command.addAll(argv);
        ProcessBuilder pb = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true)
                .redirectInput(ProcessBuilder.Redirect.from(new File(System.getProperty("os.name").startsWith("Windows")
                        ? "NUL" : "/dev/null")));
        Map<String, String> env = pb.environment();
        env.keySet().removeIf(k -> k.startsWith("XDG_"));
        env.put("HOME", home.toString());
        env.put("USERPROFILE", home.toString());
        env.put("APPDATA", home.resolve("AppData/Roaming").toString());
        env.put("XDG_CONFIG_HOME", home.resolve(".config").toString());
        env.put("XDG_DATA_HOME", home.resolve(".local/share").toString());
        env.put("XDG_STATE_HOME", home.resolve(".local/state").toString());
        env.put("XDG_CACHE_HOME", home.resolve(".cache").toString());
        env.put("OPENSPEC_TELEMETRY", "0");
        env.put("DO_NOT_TRACK", "1");
        env.put("NO_COLOR", "1");
        env.put("FORCE_COLOR", "0");
        env.put("OPENSPEC_NO_UPDATE_CHECK", "1");
        env.put("OPEN_SPEC_INTERACTIVE", "0");
        env.put("CI", "1");
        env.putAll(extraEnv);
        Process p = pb.start();
        byte[] out = p.getInputStream().readAllBytes();
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return new Run(-1, "timed out: " + String.join(" ", argv));
        }
        return new Run(p.exitValue(), new String(out, StandardCharsets.UTF_8));
    }

    static int[] nodeVersion() {
        try {
            Process p = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            String v = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim().replaceFirst("^v", "");
            p.waitFor(30, TimeUnit.SECONDS);
            String[] parts = v.split("\\.");
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (Exception e) {
            Assumptions.abort("node is not available: " + e.getMessage());
            return new int[0];
        }
    }

    static boolean atLeast(int[] have, int[] floor) {
        for (int i = 0; i < 3; i++) if (have[i] != floor[i]) return have[i] > floor[i];
        return true;
    }

    private static String firstLine(String output) {
        return output.lines().filter(l -> ARGUMENT_ERROR.matcher(l).find()).findFirst().orElse(output.strip());
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
