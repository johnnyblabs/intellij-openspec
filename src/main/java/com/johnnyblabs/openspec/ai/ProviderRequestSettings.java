package com.johnnyblabs.openspec.ai;

import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The provider-specific settings a Direct API request carries besides the key: the Claude workspace, and the
 * OpenAI organization and project. Each value is trimmed and never null; blank means unset, and an unset value
 * sends no header (a null passed in becomes blank). Each provider's request reads only its own fields.
 */
public record ProviderRequestSettings(String claudeWorkspaceId, String openAiOrganization, String openAiProject) {

    /** No provider-specific settings: no optional header is sent. */
    public static final ProviderRequestSettings NONE = new ProviderRequestSettings("", "", "");

    public ProviderRequestSettings {
        claudeWorkspaceId = clean(claudeWorkspaceId);
        openAiOrganization = clean(openAiOrganization);
        openAiProject = clean(openAiProject);
    }

    /** The values stored in the project's settings. */
    public static ProviderRequestSettings from(@NotNull OpenSpecSettings settings) {
        return new ProviderRequestSettings(settings.getAiWorkspaceId(), settings.getAiOpenAiOrganization(),
                settings.getAiOpenAiProject());
    }

    /** Only a Claude workspace. */
    public static ProviderRequestSettings claudeWorkspace(@Nullable String workspaceId) {
        return new ProviderRequestSettings(workspaceId, "", "");
    }

    /** Only an OpenAI organization and project. */
    public static ProviderRequestSettings openAi(@Nullable String organization, @Nullable String project) {
        return new ProviderRequestSettings("", organization, project);
    }

    /** Whether an OpenAI organization or project is set. */
    public boolean hasOpenAiScope() {
        return !openAiOrganization.isEmpty() || !openAiProject.isEmpty();
    }

    private static String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
