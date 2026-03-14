package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

/**
 * Resolves the delivery method based on priority:
 * saved preference → configured API → detected tools → clipboard fallback.
 */
@Service(Service.Level.PROJECT)
public final class DeliveryMethodResolver {

    private final Project project;

    public DeliveryMethodResolver(Project project) {
        this.project = project;
    }

    /**
     * Returns the resolved delivery mode and a display label for the Generate button.
     */
    public ResolvedMethod resolve() {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);

        // 1. Saved preference
        String preferred = settings.getPreferredDeliveryMethod();
        if (preferred != null && !preferred.isBlank()) {
            try {
                DeliveryMode mode = DeliveryMode.valueOf(preferred);
                return new ResolvedMethod(mode, mode.getDisplayName());
            } catch (IllegalArgumentException ignored) {
                // Invalid saved value, fall through
            }
        }

        // 2. Configured API provider
        AiProvider provider = AiProvider.fromString(settings.getAiProvider());
        if (provider != AiProvider.NONE) {
            return new ResolvedMethod(DeliveryMode.DIRECT_API,
                    "Generate via " + provider.getDisplayName());
        }

        // 3. Detected tools — suggest clipboard with tool-specific label
        AiToolDetectionService detection = project.getService(AiToolDetectionService.class);
        if (detection != null && detection.hasDetectedTools()) {
            String toolLabel = detection.getPrimaryToolLabel();
            return new ResolvedMethod(DeliveryMode.CLIPBOARD,
                    "Copy for " + toolLabel);
        }

        // 4. Fallback
        return new ResolvedMethod(DeliveryMode.CLIPBOARD, "Copy to Clipboard");
    }

    /**
     * Saves the user's chosen method as the preferred default.
     */
    public void savePreference(DeliveryMode mode) {
        OpenSpecSettings.getInstance(project).setPreferredDeliveryMethod(mode.name());
    }

    /**
     * Returns true if the user has explicitly set a preferred method.
     */
    public boolean hasPreference() {
        String preferred = OpenSpecSettings.getInstance(project).getPreferredDeliveryMethod();
        return preferred != null && !preferred.isBlank();
    }

    public record ResolvedMethod(DeliveryMode mode, String label) {
    }
}
