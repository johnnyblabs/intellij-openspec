package com.johnnyblabs.openspec.util;

import com.johnnyblabs.openspec.coordination.CoordinationService;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.ExploreContextService;
import com.johnnyblabs.openspec.services.UpdatePreviewService;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Every OpenSpec CLI invocation the plugin makes, one builder each. The runner accepts only a {@link CliArgs}, and only
 * this class can build one, so every command line the plugin can send is listed here.
 * <p>
 * The version matrix test calls each builder with sampled parameters and resolves the result against every captured
 * release, so builders follow three rules, which a hygiene test enforces: parameters are {@code String} (possibly
 * {@code @Nullable}), {@code boolean} or {@code List<String>}; command words are literals; and a builder reads nothing
 * but its parameters. {@link OfferedOn} records the releases the calling service offers it on.
 */
public final class CliCommands {

    /** The plugin's supported CLI floor. */
    public static final String FLOOR = "1.3.0";

    /** The environment variable that stops the CLI prompting; the plugin runs it without a terminal. */
    static final String NON_INTERACTIVE = "OPEN_SPEC_INTERACTIVE";

    private CliCommands() {
    }

    // ---- project setup and update -------------------------------------------------------------------------------

    /** The environment variable CLI 2.1+ reads for an init's skills scope; earlier releases ignore it. */
    static final String SKILLS_SCOPE = "OPENSPEC_SKILLS_SCOPE";

    /**
     * {@code init --tools <ids> --scope project}: {@code ids} is a comma list, {@code all} or {@code none}. The skills go
     * into the project, as every release before 2.1 put them. {@code --scope} is dropped below 2.1.0 (2.0 rejects it);
     * the environment value still reaches a 2.1 binary whose version wasn't detected, and older releases ignore it.
     */
    @OfferedOn
    public static CliArgs initInProject(String tools) {
        return CliArgs.command("init").flag("--tools", tools).optional("--scope", "project").env(SKILLS_SCOPE, "project");
    }

    /**
     * {@code init --tools <ids>} with no scope, so CLI 2.1+ keeps the project where its skills already are: a project
     * that uses the machine's skills stays on the machine.
     */
    @OfferedOn
    public static CliArgs initKeepingScope(String tools) {
        return CliArgs.command("init").flag("--tools", tools);
    }

    @OfferedOn
    public static CliArgs update() {
        return CliArgs.command("update");
    }

    /** {@code update} with prompts turned off, for a run started from the update preview. */
    @OfferedOn
    public static CliArgs updateWithoutPrompts() {
        return CliArgs.command("update").env(NON_INTERACTIVE, "0");
    }

    /** Whether {@code args} only plans ({@code --dry-run}), writing nothing. */
    public static boolean isDryRun(CliArgs args) {
        return args.requested().contains("--dry-run");
    }

    /** The read-only update plan, {@code update --dry-run [--force] --json}, with prompts turned off. */
    @OfferedOn(since = UpdatePreviewService.DRY_RUN_FLOOR)
    public static CliArgs updatePlan(boolean force) {
        CliArgs args = CliArgs.command("update").flag("--dry-run");
        if (force) args.flag("--force");
        return args.flag("--json").env(NON_INTERACTIVE, "0");
    }

    @OfferedOn
    public static CliArgs configList() {
        return CliArgs.command("config", "list").flag("--json");
    }

    @OfferedOn
    public static CliArgs configProfile(String profile) {
        return CliArgs.command("config", "profile").arg(profile);
    }

    // ---- changes and specs --------------------------------------------------------------------------------------

    @OfferedOn
    public static CliArgs validateAll(boolean strict) {
        CliArgs args = CliArgs.command("validate").flag("--all").flag("--json");
        if (strict) args.flag("--strict");
        return args;
    }

    /** {@code validate <id> --type <type> --json [--strict]}; {@code type} is {@code change} or {@code spec}. */
    @OfferedOn
    public static CliArgs validateItem(String id, String type, boolean strict) {
        CliArgs args = CliArgs.command("validate").arg(id).flag("--type", type).flag("--json");
        if (strict) args.flag("--strict");
        return args;
    }

    @OfferedOn
    public static CliArgs showChange(String name) {
        return CliArgs.command("show").arg(name).flag("--type", "change").flag("--json");
    }

    @OfferedOn(since = ChangeService.CLI_ARCHIVE_FLOOR)
    public static CliArgs archive(String change, boolean syncSpecs) {
        CliArgs args = CliArgs.command("archive").arg(change).flag("--yes").flag("--json");
        if (!syncSpecs) args.flag("--skip-specs");
        return args;
    }

    /** {@code new change <name> [--schema <schema>]}; a blank schema means the project default. */
    @OfferedOn
    public static CliArgs newChange(String name, @Nullable String schema) {
        CliArgs args = CliArgs.command("new", "change").arg(name);
        if (schema != null && !schema.isBlank()) args.flag("--schema", schema);
        return args;
    }

    @OfferedOn
    public static CliArgs list() {
        return CliArgs.command("list");
    }

    @OfferedOn(since = ExploreContextService.LIST_SPECS_FLOOR)
    public static CliArgs listSpecs() {
        return CliArgs.command("list").flag("--specs").flag("--json");
    }

    @OfferedOn
    public static CliArgs status(String change) {
        return CliArgs.command("status").flag("--change", change).flag("--json");
    }

    @OfferedOn
    public static CliArgs instructions(String artifact, String change) {
        return CliArgs.command("instructions").arg(artifact).flag("--change", change).flag("--json");
    }

    // ---- schemas ------------------------------------------------------------------------------------------------

    @OfferedOn
    public static CliArgs schemas() {
        return CliArgs.command("schemas").flag("--json");
    }

    @OfferedOn
    public static CliArgs templates(String schema) {
        return CliArgs.command("templates").flag("--schema", schema).flag("--json");
    }

    @OfferedOn
    public static CliArgs schemaFork(String source, String name) {
        return CliArgs.command("schema", "fork").arg(source).arg(name).flag("--json");
    }

    /**
     * {@code schema init <name> --json --no-default [--description <text>] [--artifacts <ids>]}. {@code --no-default}
     * is always passed: there is no terminal to answer the CLI's "set as default?" question. A blank description and
     * an empty selection are left out.
     */
    @OfferedOn
    public static CliArgs schemaInit(String name, @Nullable String description, List<String> artifacts) {
        CliArgs args = CliArgs.command("schema", "init").arg(name).flag("--json").flag("--no-default");
        if (description != null && !description.isBlank()) args.flag("--description", description.trim());
        if (artifacts != null && !artifacts.isEmpty()) args.flag("--artifacts", String.join(",", artifacts));
        return args;
    }

    @OfferedOn
    public static CliArgs schemaValidate(String name) {
        return CliArgs.command("schema", "validate").arg(name).flag("--json");
    }

    @OfferedOn
    public static CliArgs schemaWhich(String name) {
        return CliArgs.command("schema", "which").arg(name).flag("--json");
    }

    // ---- 1.4 coordination: workspace, context-store, initiative ---------------------------------------------------

    @OfferedOn(since = CoordinationService.COORDINATION_CLI_FLOOR, until = CoordinationService.COORDINATION_CLI_CEILING)
    public static CliArgs workspaceList() {
        return CliArgs.command("workspace", "list").flag("--json");
    }

    @OfferedOn(since = CoordinationService.COORDINATION_CLI_FLOOR, until = CoordinationService.COORDINATION_CLI_CEILING)
    public static CliArgs workspaceSetup(String name) {
        return CliArgs.command("workspace", "setup").flag("--name", name).flag("--no-interactive");
    }

    @OfferedOn(since = CoordinationService.COORDINATION_CLI_FLOOR, until = CoordinationService.COORDINATION_CLI_CEILING)
    public static CliArgs contextStoreList() {
        return CliArgs.command("context-store", "list").flag("--json");
    }

    @OfferedOn(since = CoordinationService.COORDINATION_CLI_FLOOR, until = CoordinationService.COORDINATION_CLI_CEILING)
    public static CliArgs contextStoreDoctor(String id) {
        return CliArgs.command("context-store", "doctor").arg(id).flag("--json");
    }

    /** {@code context-store setup [id]}; a blank id lets the CLI choose. */
    @OfferedOn(since = CoordinationService.COORDINATION_CLI_FLOOR, until = CoordinationService.COORDINATION_CLI_CEILING)
    public static CliArgs contextStoreSetup(@Nullable String id) {
        CliArgs args = CliArgs.command("context-store", "setup");
        if (id != null && !id.isBlank()) args.arg(id);
        return args;
    }

    @OfferedOn(since = CoordinationService.COORDINATION_CLI_FLOOR, until = CoordinationService.COORDINATION_CLI_CEILING)
    public static CliArgs initiativeList() {
        return CliArgs.command("initiative", "list").flag("--json");
    }

    @OfferedOn(since = CoordinationService.COORDINATION_CLI_FLOOR, until = CoordinationService.COORDINATION_CLI_CEILING)
    public static CliArgs initiativeCreate(String id, String title) {
        return CliArgs.command("initiative", "create").arg(id).flag("--title", title);
    }

    // ---- 1.5+ stores and worksets -------------------------------------------------------------------------------

    @OfferedOn(since = CoordinationService.STORE_CLI_FLOOR)
    public static CliArgs storeList() {
        return CliArgs.command("store", "list").flag("--json");
    }

    @OfferedOn(since = CoordinationService.STORE_CLI_FLOOR)
    public static CliArgs storeDoctor(String id) {
        return CliArgs.command("store", "doctor").arg(id).flag("--json");
    }

    @OfferedOn(since = CoordinationService.STORE_CLI_FLOOR)
    public static CliArgs storeSetup(String id, String path) {
        return CliArgs.command("store", "setup").arg(id).flag("--path", path).flag("--json");
    }

    /** {@code store register <path> [--yes] --json}: {@code --yes} lets the CLI create the store's identity file. */
    @OfferedOn(since = CoordinationService.STORE_CLI_FLOOR)
    public static CliArgs storeRegister(String path, boolean confirmIdentity) {
        CliArgs args = CliArgs.command("store", "register").arg(path);
        if (confirmIdentity) args.flag("--yes");
        return args.flag("--json");
    }

    @OfferedOn(since = CoordinationService.STORE_CLI_FLOOR)
    public static CliArgs storeUnregister(String id) {
        return CliArgs.command("store", "unregister").arg(id).flag("--json");
    }

    /** {@code store remove <id> --yes --json}: deletes the store's local folder. */
    @OfferedOn(since = CoordinationService.STORE_CLI_FLOOR)
    public static CliArgs storeRemove(String id) {
        return CliArgs.command("store", "remove").arg(id).flag("--yes").flag("--json");
    }

    @OfferedOn(since = CoordinationService.STORE_CLI_FLOOR)
    public static CliArgs worksetList() {
        return CliArgs.command("workset", "list").flag("--json");
    }

    /** {@code workset create <name> [--member <name=path>]... --json}. */
    @OfferedOn(since = CoordinationService.STORE_CLI_FLOOR)
    public static CliArgs worksetCreate(String name, List<String> members) {
        CliArgs args = CliArgs.command("workset", "create").arg(name);
        for (String member : members) args.flag("--member", member);
        return args.flag("--json");
    }

    @OfferedOn(since = CoordinationService.STORE_CLI_FLOOR)
    public static CliArgs worksetRemove(String name) {
        return CliArgs.command("workset", "remove").arg(name).flag("--yes").flag("--json");
    }

    @OfferedOn(since = CoordinationService.STORE_CLI_FLOOR)
    public static CliArgs doctorStore(String id) {
        return CliArgs.command("doctor").flag("--store", id).flag("--json");
    }
}
