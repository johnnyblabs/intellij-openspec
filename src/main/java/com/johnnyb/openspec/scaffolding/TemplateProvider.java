package com.johnnyb.openspec.scaffolding;

import com.johnnyb.openspec.version.VersionSupport;

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

                ## ADDED

                <!-- New requirements -->

                ## MODIFIED

                <!-- Changed requirements -->

                ## REMOVED

                <!-- Removed requirements -->
                """.formatted(domain);
    }

    public static String openspecYamlTemplate(String status) {
        return """
                schema: openspec-change
                status: %s
                created: "%s"
                """.formatted(status, LocalDate.now());
    }

    public static String configYamlTemplate(String schema, String version) {
        return """
                schema: %s
                version: "%s"
                profile:
                  name: default
                context: ""
                rules: {}
                """.formatted(schema, version);
    }

}
