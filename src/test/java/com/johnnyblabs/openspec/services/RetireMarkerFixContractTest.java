package com.johnnyblabs.openspec.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.CliFixtureSuite;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The offer to add the retirement marker, against every captured {@code retire/} refusal: offered exactly where
 * OpenSpec's fix names the marker and adding it makes the marker count, whether the refusal is the CLI's or the
 * plugin's own merge's; and once added, the plugin's merge retires every capability the CLI deletes.
 */
@CliFixtureSuite
class RetireMarkerFixContractTest {

    static final List<String> RELEASES = List.of("1.7.0", "1.8.0", "1.11.0", "1.16.1", "2.2.0");
    /** Where adding the marker is the whole fix, on every release that retires. */
    static final Set<String> OFFERED = Set.of("without-marker", "marker-not-boolean", "marker-number", "marker-null",
            "marker-yes", "marker-false", "two-capabilities-without-marker");

    @TempDir
    Path tmp;

    private record Case(String release, String name, JsonObject input, JsonObject cli, Path root) {
        String metadata() {
            return input.get("metadata").getAsString();
        }

        /** The CLI's refusal as the plugin reports it: message and fix; {@code null} when it archived. */
        String cliRefusal() {
            if (!cli.has("status") || cli.getAsJsonArray("status").isEmpty()) return null;
            JsonObject s = cli.getAsJsonArray("status").get(0).getAsJsonObject();
            return s.get("message").getAsString() + (s.has("fix") ? " " + s.get("fix").getAsString() : "");
        }

        RetirementMarkers.SchemaCheck schemas() {
            // The case's own project files decide the schema; no CLI validates the captured override.
            return n -> SpecSyncService.schemaVerdict(n, root.resolve("openspec"), root.resolve("no-user-schemas"), x -> false);
        }

        /** Each capability's delta and main spec: foo from the case, others from its extra files. */
        Map<String, String[]> capabilities() {
            Map<String, String[]> caps = new TreeMap<>();
            caps.put("foo", new String[]{input.get("delta").getAsString(),
                    input.get("spec").isJsonNull() ? null : input.get("spec").getAsString()});
            for (var e : input.getAsJsonObject("extra").entrySet()) {
                String k = e.getKey();
                if (k.startsWith("openspec/changes/c/specs/") && k.endsWith("/spec.md")) {
                    String id = k.substring("openspec/changes/c/specs/".length(), k.length() - "/spec.md".length());
                    var main = input.getAsJsonObject("extra").get("openspec/specs/" + id + "/spec.md");
                    caps.put(id, new String[]{e.getValue().getAsString(), main == null ? null : main.getAsString()});
                }
            }
            return caps;
        }

        List<SpecMerge.Result> merge(SpecMerge.RetirementMarker marker) {
            List<SpecMerge.Result> out = new ArrayList<>();
            capabilities().forEach((id, d) ->
                    out.add(SpecMerge.merge(id, "c", d[0], d[1], "openspec/specs/" + id + "/spec.md", release, marker)));
            return out;
        }

        List<String> retirableWithMarker() {
            return merge(new SpecMerge.RetirementMarker(true, null)).stream()
                    .filter(SpecMerge.Result::retired).map(SpecMerge.Result::capability).toList();
        }
    }

    private List<Case> cases(String v) throws IOException {
        Path dir = FixtureVersions.CLI.resolve(v).resolve("retire");
        List<Case> out = new ArrayList<>();
        try (var names = Files.list(dir)) {
            for (Path c : names.sorted().toList()) {
                JsonObject input = JsonParser.parseString(Files.readString(c.resolve("case.json"))).getAsJsonObject();
                Path root = tmp.resolve(v).resolve(c.getFileName().toString());
                Files.createDirectories(root.resolve("openspec"));
                for (var e : input.getAsJsonObject("extra").entrySet()) {
                    Files.createDirectories(root.resolve(e.getKey()).getParent());
                    Files.writeString(root.resolve(e.getKey()), e.getValue().getAsString());
                }
                out.add(new Case(v, c.getFileName().toString(), input,
                        JsonParser.parseString(Files.readString(c.resolve("json.out"))).getAsJsonObject(), root));
            }
        }
        assertTrue(out.size() >= 27, v + ": sanity, " + out.size() + " cases");
        return out;
    }

    private static RetireMarkerFix.Offer offer(Case c, List<String> refusals) {
        return RetireMarkerFix.decide("c", refusals, c.metadata(), c.schemas(), c::retirableWithMarker);
    }

    @Test
    void theCliRefusalOffersTheMarkerExactlyWhereItIsTheFix() throws IOException {
        for (String v : RELEASES) {
            Set<String> offered = new TreeSet<>();
            for (Case c : cases(v)) {
                String refusal = c.cliRefusal();
                if (refusal != null && offer(c, List.of(refusal)) != null) offered.add(c.name());
            }
            assertEquals(v.equals("1.7.0") ? Set.of() : new TreeSet<>(OFFERED), offered, v);
        }
    }

    @Test
    void thePluginsOwnRefusalOffersTheSame() throws IOException {
        for (String v : RELEASES) {
            Set<String> offered = new TreeSet<>();
            for (Case c : cases(v)) {
                SpecMerge.RetirementMarker marker = RetirementMarkers.parse(c.metadata(), c.schemas());
                List<String> refusals = c.merge(marker).stream()
                        .map(SpecMerge.Result::refusal).filter(r -> r != null).toList();
                if (!refusals.isEmpty() && offer(c, refusals) != null) offered.add(c.name());
            }
            assertEquals(v.equals("1.7.0") ? Set.of() : new TreeSet<>(OFFERED), offered, v);
        }
    }

    /** The plugin leaves out a reason no capture pins; only the edit check then keeps the offer away. */
    @Test
    void anInvalidSchemaOverrideIsNotOfferedThoughThePluginGivesNoReason() throws IOException {
        Case c = cases("2.2.0").stream().filter(k -> k.name().equals("marker-invalid-project-schema")).findFirst().orElseThrow();
        assertFalse(RetireMarkerFix.asksForMarker(c.cliRefusal()), "the CLI's reason names the schema");
        String plugin = c.merge(RetirementMarkers.parse(c.metadata(), c.schemas())).getFirst().refusal();
        assertTrue(RetireMarkerFix.asksForMarker(plugin), "the plugin's refusal passes the text gate: " + plugin);
        assertNull(offer(c, List.of(plugin)), "the edit check stops it");
    }

    /** Once the marker is added, the plugin's merge retires what the CLI deletes when the marker is there. */
    @Test
    void afterTheEditTheMergeRetiresEveryCapabilityTheCliDeletes() throws IOException {
        int checked = 0;
        for (String v : RELEASES.subList(1, RELEASES.size())) {
            Map<String, Case> byName = new TreeMap<>();
            for (Case c : cases(v)) byName.put(c.name(), c);
            for (String name : OFFERED) {
                Case c = byName.get(name);
                SpecMerge.RetirementMarker edited = RetirementMarkers.parse(RetireMarkerFix.withMarker(c.metadata()), c.schemas());
                assertTrue(edited.declared(), v + " " + name);
                List<SpecMerge.Result> merged = c.merge(edited);
                assertTrue(merged.stream().allMatch(r -> r.retired() && r.refusal() == null), v + " " + name);
                RetireMarkerFix.Offer offer = offer(c, List.of(c.cliRefusal()));
                assertEquals(merged.stream().map(SpecMerge.Result::capability).toList(), offer.capabilities(), v + " " + name);
                assertEquals(name.equals("marker-false"), offer.replacesFalse(), v + " " + name + ": an explicit false is named");
                checked++;
            }
            // The marked two-capability run: the CLI deletes both specs, and names only foo when refusing unmarked.
            Case two = byName.get("two-capabilities");
            assertTrue(json(FixtureVersions.CLI.resolve(v).resolve("retire/two-capabilities/after.json")).isEmpty(), v);
            assertTrue(byName.get("two-capabilities-without-marker").cliRefusal().contains("requirement 'foo' has"), v);
            assertFalse(byName.get("two-capabilities-without-marker").cliRefusal().contains("'zoo'"), v);
            assertEquals(List.of("foo", "zoo"), two.retirableWithMarker(), v);
        }
        assertEquals(4 * OFFERED.size(), checked);
    }

    private static JsonObject json(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }
}
