package com.johnnyblabs.openspec.actions;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.util.CliFixtures;
import com.johnnyblabs.openspec.util.FixtureVersions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Upstream workflow parity: every workflow upstream OpenSpec defines either has a registered action gated on it
 * ({@link OpenSpecBaseAction#getWorkflowId()}) or is listed in {@link #NOT_SURFACED} with the reason, and no action is
 * gated on a workflow upstream doesn't have. The upstream set is captured from the real CLI package by
 * {@code scripts/capture-workflow-ids.py}. Re-capture it on every CLI bump; a new upstream workflow then fails here
 * until it's mapped or explained.
 *
 * <p>This checks structure only. Whether an action does what its workflow does, and whether users can find it, is the
 * workflow-to-UI parity audit's job, not this test's.
 *
 * <p>No behaviour exception is recorded. <b>Propose</b> does upstream {@code propose} (create the change and every
 * artifact it needs before apply, or hand the description to the AI tool's propose workflow), and <b>New Change</b>
 * does upstream {@code new}. (The v0.17.0 Propose exception and the v0.16.0 Apply exception are resolved.)
 */
public class WorkflowParityContractTest extends BasePlatformTestCase {

    /**
     * Every release with a captured upstream workflow set. The plugin serves each of them, so parity holds for each;
     * a missing capture fails rather than shrinking the check.
     */
    static final Set<String> FLOOR = Set.of("1.15.0", "1.16.0", "1.16.1", "1.17.0", "2.0.0", "2.1.0", "2.1.1");

    static List<String> releases() {
        List<String> found = FixtureVersions.versionsWith("workflows.json", FLOOR);
        assertTrue("the target release is checked", found.contains(CliFixtures.targetVersion()));
        return found;
    }

    /** Upstream workflows the plugin deliberately has no action gated on, each with why and where it's tracked. */
    static final Map<String, String> NOT_SURFACED = Map.of(
            "onboard", "undecided whether the plugin offers a guided tour (tracked with the menu alignment work)",
            "sync", "Sync Specs is the built-in merge that writes main specs without the CLI or an agent, deliberately not gated on the agent workflow");

    /** Workflow-gated actions registered today; the test must find at least these, so it can't pass on an empty list. */
    private static final Set<String> KNOWN_GATED = Set.of(
            "propose", "new", "explore", "continue", "update", "apply", "ff", "archive", "bulk-archive", "verify");

    private static List<String> upstream(String version, String key) {
        String fixture = "/fixtures/cli/" + version + "/workflows.json";
        try (InputStream in = WorkflowParityContractTest.class.getResourceAsStream(fixture)) {
            assertNotNull("missing fixture " + fixture, in);
            JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> ids = new ArrayList<>();
            for (var el : json.getAsJsonArray(key)) ids.add(el.getAsString());
            return ids;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Workflow id → the registered OpenSpec action ids gated on it, read from plugin.xml and the live ActionManager. */
    private static Map<String, List<String>> gatedActions() throws IOException {
        String pluginXml = Files.readString(Path.of("src/main/resources/META-INF/plugin.xml"));
        Matcher m = Pattern.compile("<action\\s+id=\"(OpenSpec\\.[^\"]+)\"").matcher(pluginXml);
        Map<String, List<String>> gated = new TreeMap<>();
        while (m.find()) {
            AnAction action = ActionManager.getInstance().getAction(m.group(1));
            assertNotNull("plugin.xml registers " + m.group(1) + " but ActionManager has no such action", action);
            if (action instanceof OpenSpecBaseAction base && base.getWorkflowId() != null) {
                gated.computeIfAbsent(base.getWorkflowId(), k -> new ArrayList<>()).add(m.group(1));
            }
        }
        return gated;
    }

    public void testFindsTheKnownWorkflowGatedActions() throws IOException {
        Set<String> found = gatedActions().keySet();
        assertTrue("expected at least " + KNOWN_GATED + " but found " + found, found.containsAll(KNOWN_GATED));
    }

    public void testEveryUpstreamWorkflowIsGatedOrExplained() throws IOException {
        Set<String> gated = gatedActions().keySet();
        List<String> unmapped = new ArrayList<>();
        for (String v : releases()) {
            for (String workflow : upstream(v, "all")) {
                if (!gated.contains(workflow) && !NOT_SURFACED.containsKey(workflow)) unmapped.add(v + " " + workflow);
            }
        }
        assertTrue("upstream workflows with no gated action and no NOT_SURFACED entry: " + unmapped
                + ". Add an action gated on each (getWorkflowId), or a NOT_SURFACED entry saying why not.",
                unmapped.isEmpty());
    }

    public void testNoActionIsGatedOnAWorkflowUpstreamDoesNotHave() throws IOException {
        Map<String, List<String>> gated = gatedActions();
        Map<String, List<String>> unknown = new LinkedHashMap<>();
        for (String v : releases()) {
            Set<String> upstream = new LinkedHashSet<>(upstream(v, "all"));
            gated.forEach((workflow, actions) -> {
                if (!upstream.contains(workflow)) unknown.put(v + " " + workflow, actions);
            });
        }
        assertTrue("actions gated on workflow ids upstream doesn't have (misspelled or removed upstream): " + unknown,
                unknown.isEmpty());
    }

    public void testNotSurfacedHasNoStaleEntries() throws IOException {
        Map<String, List<String>> gated = gatedActions();
        // An entry is stale once no captured release has the workflow.
        Set<String> upstream = new LinkedHashSet<>();
        for (String v : releases()) upstream.addAll(upstream(v, "all"));
        List<String> stale = new ArrayList<>();
        for (String workflow : NOT_SURFACED.keySet()) {
            if (gated.containsKey(workflow)) stale.add(workflow + " (now gated by " + gated.get(workflow) + ")");
            else if (!upstream.contains(workflow)) stale.add(workflow + " (no longer an upstream workflow)");
        }
        assertTrue("stale NOT_SURFACED entries, remove them: " + stale, stale.isEmpty());
    }

    public void testTheFixtureIsUpstreamsShape() {
        for (String v : releases()) {
            List<String> all = upstream(v, "all");
            List<String> core = upstream(v, "core");
            assertTrue(v + ": every core workflow is in the full set", all.containsAll(core));
            assertEquals(v + ": no duplicate workflow ids", all.size(), new LinkedHashSet<>(all).size());
        }
    }
}
