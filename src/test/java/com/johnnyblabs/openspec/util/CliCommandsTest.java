package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Each {@link CliCommands} builder's exact command line, as the plugin sent it before the catalog existed. The version
 * matrix test proves the arguments are accepted; this one pins which arguments they are, so a changed flag order,
 * a dropped {@code --json} or a lost branch is a named failure.
 */
class CliCommandsTest {

    /** Builder name → every command line it can produce here, as built (before any release is applied). */
    static final Map<String, List<List<String>>> EXPECTED = new LinkedHashMap<>();

    private static void expect(String builder, CliArgs actual, String... argv) {
        EXPECTED.computeIfAbsent(builder, k -> new ArrayList<>()).add(List.of(argv));
        assertEquals(List.of(argv), actual.requested(), builder);
    }

    @Test
    void everyBuilderBuildsExactlyTheCommandLineThePluginSends() {
        expect("init", CliCommands.init("claude,cursor"), "init", "--tools", "claude,cursor");
        expect("update", CliCommands.update(), "update");
        expect("updateWithoutPrompts", CliCommands.updateWithoutPrompts(), "update");
        expect("updatePlan", CliCommands.updatePlan(false), "update", "--dry-run", "--json");
        expect("updatePlan", CliCommands.updatePlan(true), "update", "--dry-run", "--force", "--json");
        expect("configList", CliCommands.configList(), "config", "list", "--json");
        expect("configProfile", CliCommands.configProfile("custom"), "config", "profile", "custom");
        expect("validateAll", CliCommands.validateAll(false), "validate", "--all", "--json");
        expect("validateAll", CliCommands.validateAll(true), "validate", "--all", "--json", "--strict");
        expect("validateItem", CliCommands.validateItem("demo", "spec", false),
                "validate", "demo", "--type", "spec", "--json");
        expect("validateItem", CliCommands.validateItem("demo", "change", true),
                "validate", "demo", "--type", "change", "--json", "--strict");
        expect("showChange", CliCommands.showChange("demo"), "show", "demo", "--type", "change", "--json");
        expect("archive", CliCommands.archive("demo", true), "archive", "demo", "--yes", "--json");
        expect("archive", CliCommands.archive("demo", false), "archive", "demo", "--yes", "--json", "--skip-specs");
        expect("newChange", CliCommands.newChange("demo", "minimal"), "new", "change", "demo", "--schema", "minimal");
        expect("newChange", CliCommands.newChange("demo", null), "new", "change", "demo");
        expect("newChange", CliCommands.newChange("demo", ""), "new", "change", "demo");
        expect("list", CliCommands.list(), "list");
        expect("listSpecs", CliCommands.listSpecs(), "list", "--specs", "--json");
        expect("status", CliCommands.status("demo"), "status", "--change", "demo", "--json");
        expect("instructions", CliCommands.instructions("proposal", "demo"),
                "instructions", "proposal", "--change", "demo", "--json");
        expect("schemas", CliCommands.schemas(), "schemas", "--json");
        expect("templates", CliCommands.templates("spec-driven"), "templates", "--schema", "spec-driven", "--json");
        expect("schemaFork", CliCommands.schemaFork("spec-driven", "mine"), "schema", "fork", "spec-driven", "mine", "--json");
        expect("schemaInit", CliCommands.schemaInit("flow", " My flow ", List.of("proposal", "tasks")),
                "schema", "init", "flow", "--json", "--no-default", "--description", "My flow",
                "--artifacts", "proposal,tasks");
        expect("schemaInit", CliCommands.schemaInit("flow", null, List.of()),
                "schema", "init", "flow", "--json", "--no-default");
        expect("schemaValidate", CliCommands.schemaValidate("flow"), "schema", "validate", "flow", "--json");
        expect("schemaWhich", CliCommands.schemaWhich("flow"), "schema", "which", "flow", "--json");
        expect("workspaceList", CliCommands.workspaceList(), "workspace", "list", "--json");
        expect("workspaceSetup", CliCommands.workspaceSetup("ws"),
                "workspace", "setup", "--name", "ws", "--no-interactive");
        expect("contextStoreList", CliCommands.contextStoreList(), "context-store", "list", "--json");
        expect("contextStoreDoctor", CliCommands.contextStoreDoctor("cs"), "context-store", "doctor", "cs", "--json");
        expect("contextStoreSetup", CliCommands.contextStoreSetup("cs"), "context-store", "setup", "cs");
        expect("contextStoreSetup", CliCommands.contextStoreSetup(null), "context-store", "setup");
        expect("contextStoreSetup", CliCommands.contextStoreSetup(" "), "context-store", "setup");
        expect("initiativeList", CliCommands.initiativeList(), "initiative", "list", "--json");
        expect("initiativeCreate", CliCommands.initiativeCreate("i1", "Title"),
                "initiative", "create", "i1", "--title", "Title");
        expect("storeList", CliCommands.storeList(), "store", "list", "--json");
        expect("storeDoctor", CliCommands.storeDoctor("s"), "store", "doctor", "s", "--json");
        expect("storeSetup", CliCommands.storeSetup("s", "/p"), "store", "setup", "s", "--path", "/p", "--json");
        expect("storeRegister", CliCommands.storeRegister("/p", false), "store", "register", "/p", "--json");
        expect("storeRegister", CliCommands.storeRegister("/p", true), "store", "register", "/p", "--yes", "--json");
        expect("storeUnregister", CliCommands.storeUnregister("s"), "store", "unregister", "s", "--json");
        expect("storeRemove", CliCommands.storeRemove("s"), "store", "remove", "s", "--yes", "--json");
        expect("worksetList", CliCommands.worksetList(), "workset", "list", "--json");
        expect("worksetCreate", CliCommands.worksetCreate("w", List.of("a=/x", "b=/y")),
                "workset", "create", "w", "--member", "a=/x", "--member", "b=/y", "--json");
        expect("worksetRemove", CliCommands.worksetRemove("w"), "workset", "remove", "w", "--yes", "--json");
        expect("doctorStore", CliCommands.doctorStore("s"), "doctor", "--store", "s", "--json");

        Set<String> builders = new TreeSet<>();
        for (Method m : CliCommands.class.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && m.getReturnType() == CliArgs.class) builders.add(m.getName());
        }
        assertEquals(builders, new TreeSet<>(EXPECTED.keySet()), "every builder has its command line pinned here");
    }

    @Test
    void theUpdatePreviewRunsWithoutPrompts() {
        assertEquals(Map.of("OPEN_SPEC_INTERACTIVE", "0"), CliCommands.updatePlan(false).env());
        assertEquals(Map.of("OPEN_SPEC_INTERACTIVE", "0"), CliCommands.updateWithoutPrompts().env());
        assertEquals(Map.of(), CliCommands.update().env());
    }
}
