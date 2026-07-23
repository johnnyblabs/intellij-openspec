package com.johnnyblabs.openspec.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.model.DeltaSpecOperation.OperationType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure model for a change's assembled spec-level deltas, parsed from the OpenSpec CLI's
 * {@code openspec show <change> --type change --json} output. Headless — Gson only, no platform
 * types — so it is contract-testable against captured real CLI output.
 *
 * <p>Verified CLI shape ({@code {id,title,deltaCount,deltas[]}}): every non-{@code RENAMED} delta
 * carries BOTH a singular {@code requirement{text,scenarios[].rawText}} and a one-element
 * {@code requirements[]} mirror (the CLI splits a multi-requirement block into one delta per
 * requirement, so {@code requirement == requirements[0]}). {@code REMOVED} carries its
 * {@code requirement.text} with an empty {@code scenarios} array. Only {@code RENAMED} is
 * requirement-less, carrying {@code rename{from,to}} instead. Parsing is null-safe on all of these.
 *
 * <p>Cross-capability ordering from the CLI is {@code readdir}-dependent and NOT a contract, so
 * {@link #groupedByCapability()} imposes the plugin's own stable alphabetical sort; within a
 * capability the CLI's operation order (ADDED → MODIFIED → REMOVED → RENAMED, then authored) is
 * deterministic and preserved.
 */
public final class ChangeDeltaModel {

    /** A scenario's raw markdown text (WHEN/THEN bullet block), verbatim from the CLI. */
    public record Scenario(String rawText) {}

    /** A requirement delta body: its text and (possibly empty) scenarios. */
    public record Requirement(String text, List<Scenario> scenarios) {
        public Requirement {
            scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
        }
    }

    /** A rename delta's from/to requirement names (only present on {@code RENAMED}). */
    public record Rename(String from, String to) {}

    /**
     * A single delta. {@code requirement}/{@code rename} are nullable and mutually exclusive by
     * operation: ADDED/MODIFIED/REMOVED carry {@code requirement} (and its {@code requirementsMirror}
     * copy); RENAMED carries {@code rename} and a {@code null} requirement.
     */
    public record Delta(
            String spec,
            OperationType operation,
            String description,
            Requirement requirement,
            List<Requirement> requirementsMirror,
            Rename rename) {
        public Delta {
            requirementsMirror = requirementsMirror == null ? List.of() : List.copyOf(requirementsMirror);
        }
    }

    /** A capability's deltas, in the CLI's (preserved) within-capability order. */
    public record CapabilityGroup(String capability, List<Delta> deltas) {
        public CapabilityGroup {
            deltas = List.copyOf(deltas);
        }
    }

    private final String id;
    private final String title;
    private final int deltaCount;
    private final List<Delta> deltas;

    public ChangeDeltaModel(String id, String title, int deltaCount, List<Delta> deltas) {
        this.id = id == null ? "" : id;
        this.title = title == null ? this.id : title;
        this.deltaCount = deltaCount;
        this.deltas = deltas == null ? List.of() : List.copyOf(deltas);
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    /** The CLI's reported delta count (authoritative; may differ from {@code deltas().size()} only on drift). */
    public int deltaCount() {
        return deltaCount;
    }

    public List<Delta> deltas() {
        return deltas;
    }

    /** Number of distinct capabilities touched. */
    public int capabilityCount() {
        Set<String> caps = new LinkedHashSet<>();
        for (Delta d : deltas) {
            caps.add(d.spec());
        }
        return caps.size();
    }

    /** How many deltas carry the given operation. */
    public int operationCount(OperationType type) {
        int n = 0;
        for (Delta d : deltas) {
            if (d.operation() == type) {
                n++;
            }
        }
        return n;
    }

    /**
     * Deltas grouped by capability, capability groups ordered by the plugin's own stable alphabetical
     * sort; within a group the CLI's delta order is preserved. This is the falsifiable seam the render
     * test's "sort trap" targets: reverse-alphabetical input must still render alphabetically.
     */
    public List<CapabilityGroup> groupedByCapability() {
        Map<String, List<Delta>> byCap = new LinkedHashMap<>();
        for (Delta d : deltas) {
            byCap.computeIfAbsent(d.spec(), k -> new ArrayList<>()).add(d);
        }
        List<String> caps = new ArrayList<>(byCap.keySet());
        caps.sort(Comparator.naturalOrder());
        List<CapabilityGroup> groups = new ArrayList<>(caps.size());
        for (String cap : caps) {
            groups.add(new CapabilityGroup(cap, byCap.get(cap)));
        }
        return groups;
    }

    /**
     * The CLI argv that produces a change's delta JSON. Pure and unit-tested so the exact contract
     * ({@code show <name> --type change --json}) can't silently drift; the deprecated {@code change
     * show} noun form and the {@code --deltas-only}/{@code --requirements-only} flags are deliberately
     * NOT used (see the change's design). Read stdout only — stderr carries a spurious flag warning.
     */
    public static String[] changeShowArgs(String name) {
        return new String[] {"show", name, "--type", "change", "--json"};
    }

    /**
     * Parses the CLI's change-show stdout JSON into the model. A null/blank input yields an empty
     * model (deltaCount 0) rather than throwing. Reads the singular {@code requirement} for the delta
     * body while still capturing {@code requirements[]} so the contract test can assert the mirror
     * invariant.
     */
    public static ChangeDeltaModel parse(String stdoutJson) {
        if (stdoutJson == null || stdoutJson.isBlank()) {
            return new ChangeDeltaModel("", "", 0, List.of());
        }
        JsonObject root = JsonParser.parseString(stdoutJson).getAsJsonObject();
        String id = optString(root, "id", "");
        String title = optString(root, "title", id);
        int deltaCount = (root.has("deltaCount") && !root.get("deltaCount").isJsonNull())
                ? root.get("deltaCount").getAsInt() : 0;
        List<Delta> deltas = new ArrayList<>();
        if (root.has("deltas") && root.get("deltas").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("deltas");
            for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                    deltas.add(parseDelta(el.getAsJsonObject()));
                }
            }
        }
        return new ChangeDeltaModel(id, title, deltaCount, deltas);
    }

    private static Delta parseDelta(JsonObject o) {
        String spec = optString(o, "spec", "");
        OperationType operation = parseOperation(optString(o, "operation", ""));
        String description = optString(o, "description", "");

        Requirement requirement = null;
        if (o.has("requirement") && o.get("requirement").isJsonObject()) {
            requirement = parseRequirement(o.getAsJsonObject("requirement"));
        }

        List<Requirement> mirror = new ArrayList<>();
        if (o.has("requirements") && o.get("requirements").isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray("requirements")) {
                if (el.isJsonObject()) {
                    mirror.add(parseRequirement(el.getAsJsonObject()));
                }
            }
        }

        Rename rename = null;
        if (o.has("rename") && o.get("rename").isJsonObject()) {
            JsonObject r = o.getAsJsonObject("rename");
            rename = new Rename(optString(r, "from", ""), optString(r, "to", ""));
        }

        return new Delta(spec, operation, description, requirement, mirror, rename);
    }

    private static Requirement parseRequirement(JsonObject o) {
        String text = optString(o, "text", "");
        List<Scenario> scenarios = new ArrayList<>();
        if (o.has("scenarios") && o.get("scenarios").isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray("scenarios")) {
                if (el.isJsonObject()) {
                    scenarios.add(new Scenario(optString(el.getAsJsonObject(), "rawText", "")));
                }
            }
        }
        return new Requirement(text, scenarios);
    }

    private static OperationType parseOperation(String raw) {
        // The CLI emits exactly ADDED/MODIFIED/REMOVED/RENAMED; contract-tested against real output.
        return OperationType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    }

    private static String optString(JsonObject o, String key, String fallback) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return fallback;
        }
        return o.get(key).getAsString();
    }
}
