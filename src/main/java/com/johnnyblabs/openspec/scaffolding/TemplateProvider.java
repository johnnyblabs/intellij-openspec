package com.johnnyblabs.openspec.scaffolding;

import com.johnnyblabs.openspec.version.VersionSupport;

import java.time.LocalDate;

public final class TemplateProvider {

    private TemplateProvider() {
    }

    public static String proposalTemplate(String changeName, String why, String whatChanges) {
        String whyContent = (why == null || why.isBlank())
                ? "<!-- Explain the motivation for this change. What problem does this solve? Why now? -->"
                : why;
        String whatChangesContent = (whatChanges == null || whatChanges.isBlank())
                ? "<!-- Describe what will change. Be specific about new capabilities, modifications, or removals. -->"
                : whatChanges;

        return """
                ## Why

                %s

                ## What Changes

                %s

                ## Capabilities

                ### New Capabilities
                <!-- Capabilities being introduced. Use kebab-case identifiers (e.g., user-auth, data-export). Each creates specs/<name>/spec.md -->

                ### Modified Capabilities
                <!-- Existing capabilities whose REQUIREMENTS are changing. Use existing spec names from openspec/specs/. -->

                ## Impact

                <!-- Affected code, APIs, dependencies, systems -->
                """.formatted(whyContent, whatChangesContent);
    }

    public static String designTemplate(String changeName) {
        return """
                # Design: %s

                ## Approach

                <!-- Describe the technical approach -->

                ## Components Affected

                <!-- List affected components -->

                ## Trade-offs

                <!-- Document any trade-offs -->
                """.formatted(changeName);
    }

    public static String tasksTemplate(String changeName) {
        return """
                # Tasks: %s

                ## Implementation Tasks

                - [ ] Task 1
                - [ ] Task 2
                - [ ] Task 3

                ## Testing Tasks

                - [ ] Write unit tests
                - [ ] Integration testing
                """.formatted(changeName);
    }

    public static String deltaSpecTemplate(String domain) {
        return """
                # Delta Spec: %s

                ## ADDED Requirements

                <!-- New requirements: one "### Requirement: <name>" block each, with at least one "#### Scenario:". -->

                ## MODIFIED Requirements

                <!-- Changed requirements: the full updated "### Requirement: <name>" block, with at least one "#### Scenario:". -->

                ## REMOVED Requirements

                <!-- Removed requirements: the "### Requirement: <name>" block, with **Reason** and **Migration**. -->

                ## RENAMED Requirements

                <!-- Renamed requirements as FROM:/TO: pairs, e.g. - FROM: `### Requirement: Old name` then - TO: `### Requirement: New name` -->
                """.formatted(domain);
    }

    public static String openspecYamlTemplate(String schema) {
        // Matches real `openspec new change`: the effective schema, an UNQUOTED created date, no status.
        // (The plugin no longer writes the invented `openspec-change` schema value or `status:` field —
        // OpenSpec has no change-status concept; active vs archived is directory location.)
        return """
                schema: %s
                created: %s
                """.formatted(schema, LocalDate.now());
    }

    /**
     * A fresh {@code openspec/config.yaml}, matching upstream {@code openspec init}: a single
     * {@code schema:} line and nothing else. The plugin deliberately does NOT write the plugin-invented
     * {@code version:}/{@code profile:} fields (upstream's Zod schema ignores them; they are cosmetic
     * display-only) or empty {@code context:}/{@code rules:} — that pollutes a file upstream owns.
     */
    public static String configYamlTemplate(String schema) {
        return "schema: %s\n".formatted(schema);
    }

}
