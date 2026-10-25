package com.johnnyblabs.openspec.actions;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

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
 * <p>This checks structure only. Whether an action does what its workflow does (Propose currently behaves like
 * {@code new}), and whether users can find it, is the workflow-to-UI parity audit's job, not this test's.
 */
public class WorkflowParityContractTest extends BasePlatformTestCase {

    private static final String FIXTURE = "/fixtures/cli/1.15.0/workflows.json";

    /** Upstream workflows the plugin deliberately has no action gated on, each with why and where it's tracked. */
    static final Map<String, String> NOT_SURFACED = Map.of(
            "new", "no scaffold-only action yet; Propose currently behaves like it (tracked: Propose = upstream propose, New Change = new)",
            "onboard", "undecided whether the plugin offers a guided tour (tracked with the menu alignment work)",
            "sync", "the built-in Sync Specs action is a utility, deliberately not gated on the workflow");

    /** Workflow-gated actions registered today; the test must find at least these, so it can't pass on an empty list. */
    private static final Set<String> KNOWN_GATED = Set.of(
            "propose", "explore", "continue", "update", "apply", "ff", "archive", "bulk-archive", "verify");

    private static List<String> upstream(String key) {
        try (InputStream in = WorkflowParityContractTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull("missing fixture " + FIXTURE, in);
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
        for (String workflow : upstream("all")) {
            if (!gated.contains(workflow) && !NOT_SURFACED.containsKey(workflow)) unmapped.add(workflow);
        }
        assertTrue("upstream workflows with no gated action and no NOT_SURFACED entry: " + unmapped
                + ". Add an action gated on each (getWorkflowId), or a NOT_SURFACED entry saying why not.",
                unmapped.isEmpty());
    }

    public void testNoActionIsGatedOnAWorkflowUpstreamDoesNotHave() throws IOException {
        Set<String> upstream = new LinkedHashSet<>(upstream("all"));
        Map<String, List<String>> unknown = new LinkedHashMap<>();
        gatedActions().forEach((workflow, actions) -> {
            if (!upstream.contains(workflow)) unknown.put(workflow, actions);
        });
        assertTrue("actions gated on workflow ids upstream doesn't have (misspelled or removed upstream): " + unknown,
                unknown.isEmpty());
    }

    public void testNotSurfacedHasNoStaleEntries() throws IOException {
        Map<String, List<String>> gated = gatedActions();
        Set<String> upstream = new LinkedHashSet<>(upstream("all"));
        List<String> stale = new ArrayList<>();
        for (String workflow : NOT_SURFACED.keySet()) {
            if (gated.containsKey(workflow)) stale.add(workflow + " (now gated by " + gated.get(workflow) + ")");
            else if (!upstream.contains(workflow)) stale.add(workflow + " (no longer an upstream workflow)");
        }
        assertTrue("stale NOT_SURFACED entries, remove them: " + stale, stale.isEmpty());
    }

    public void testTheFixtureIsUpstreamsShape() {
        List<String> all = upstream("all");
        List<String> core = upstream("core");
        assertTrue("every core workflow is in the full set", all.containsAll(core));
        assertEquals("no duplicate workflow ids", all.size(), new LinkedHashSet<>(all).size());
    }
}
