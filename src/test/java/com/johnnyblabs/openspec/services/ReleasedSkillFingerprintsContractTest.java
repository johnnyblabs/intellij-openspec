package com.johnnyblabs.openspec.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ReleasedSkillFingerprints} agrees with upstream's {@code generated-fingerprint.js}, for every 2.x release that
 * has vectors ({@code fixtures/cli/<version>/fingerprints/vectors.json}, computed by upstream's own functions on real
 * generated skills and on a BOM, CRLF, {@code generatedBy} and one-character edits of them). The shipped set must equal
 * each release's set, by count and digest.
 */
class ReleasedSkillFingerprintsContractTest {

    /** Releases whose vectors must exist, so deleting a capture can't shrink the check. */
    static final Set<String> REQUIRED = Set.of("2.0.0", "2.1.0", "2.1.1");

    @TestFactory
    Stream<DynamicTest> matchesUpstreamOnEveryVector() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (String v : FixtureVersions.versionsWith("fingerprints/vectors.json", REQUIRED)) {
            JsonObject root = vectors(v);
            int released = 0, notReleased = 0;
            for (JsonElement e : root.getAsJsonArray("cases")) {
                JsonObject c = e.getAsJsonObject();
                if (c.get("released").getAsBoolean()) released++; else notReleased++;
                String name = c.get("name").getAsString();
                tests.add(DynamicTest.dynamicTest(v + "/" + name, () -> {
                    String content = c.get("content").getAsString();
                    assertEquals(c.get("normalized").getAsString(), ReleasedSkillFingerprints.normalize(content), "normalized");
                    assertEquals(c.get("fingerprint").getAsString(), ReleasedSkillFingerprints.fingerprint(content), "fingerprint");
                    assertEquals(c.get("released").getAsBoolean(), ReleasedSkillFingerprints.isReleased(content), "released");
                }));
            }
            // Both verdicts must be exercised, or a gate stuck at one answer would pass.
            int r = released, n = notReleased;
            tests.add(DynamicTest.dynamicTest(v + "/both-verdicts", () -> {
                assertTrue(r >= 2, "released vectors in " + v);
                assertTrue(n >= 2, "non-released vectors in " + v);
            }));
        }
        return tests.stream();
    }

    @TestFactory
    Stream<DynamicTest> shippedSetEqualsEachReleasesSet() throws Exception {
        List<String> lines = shippedFingerprints();
        String digest = sha256(String.join("\n", lines) + "\n");
        List<DynamicTest> tests = new ArrayList<>();
        for (String v : FixtureVersions.versionsWith("fingerprints/vectors.json", REQUIRED)) {
            JsonObject set = vectors(v).getAsJsonObject("set");
            tests.add(DynamicTest.dynamicTest(v, () -> {
                assertEquals(set.get("count").getAsInt(), lines.size(), "fingerprint count");
                assertEquals(set.get("count").getAsInt(), ReleasedSkillFingerprints.size(), "loaded count");
                assertEquals(set.get("sha256").getAsString(), digest,
                        "shipped set differs from CLI " + v + "'s; rerun scripts/capture-released-fingerprints.py");
            }));
        }
        return tests.stream();
    }

    private static JsonObject vectors(String v) throws IOException {
        return JsonParser.parseString(Files.readString(
                FixtureVersions.CLI.resolve(v).resolve("fingerprints/vectors.json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** The resource's fingerprints, sorted, read the way a Windows checkout may hand it over (CRLF tolerated). */
    private static List<String> shippedFingerprints() throws IOException {
        try (InputStream in = ReleasedSkillFingerprints.class.getResourceAsStream(ReleasedSkillFingerprints.RESOURCE)) {
            assertNotNull(in, "resource " + ReleasedSkillFingerprints.RESOURCE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip).filter(s -> !s.isEmpty() && !s.startsWith("#")).sorted().toList();
        }
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
