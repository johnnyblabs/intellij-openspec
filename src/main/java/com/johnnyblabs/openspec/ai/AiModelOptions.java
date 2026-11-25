package com.johnnyblabs.openspec.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure resolution of the Model selector's options and selection from a list-models fetch result.
 *
 * <p>Keeping this off the Swing panel makes the populate logic unit-testable without a live dialog or
 * the EDT: the settings panel's {@code done()} becomes a thin apply-loop
 * ({@code removeAllItems}/{@code addItem}/{@code setSelectedItem}) over what this returns. The rules:
 * a successful non-empty fetch supplies the options, otherwise the static fallback does; the user's
 * current selection is always preserved — if it is a typed-in value not in the list, it is kept
 * (prepended) rather than silently replaced (the editable-override guarantee). With no current
 * selection, the provider's static default is selected when the list has it, because a fetched list's
 * first entry can be an access-limited or non-chat model.
 */
public final class AiModelOptions {

    private AiModelOptions() {
    }

    /** The resolved dropdown options (in order) and the item that should be selected. */
    public record Resolution(List<String> options, String selection) {
    }

    /**
     * @param fetched         models returned by the list-models fetch (may be null/empty)
     * @param fetchOk         whether the fetch succeeded (a failure falls back regardless of {@code fetched})
     * @param staticFallback  the provider's static model list, used when the fetch is unavailable
     * @param currentSelection the user's current model, preserved across the repopulate
     */
    public static Resolution resolve(List<String> fetched, boolean fetchOk,
                                     List<String> staticFallback, String currentSelection) {
        List<String> options = (fetchOk && fetched != null && !fetched.isEmpty())
                ? new ArrayList<>(fetched)
                : new ArrayList<>(staticFallback == null ? List.of() : staticFallback);

        String current = currentSelection == null ? "" : currentSelection.trim();
        if (!current.isEmpty() && !options.contains(current)) {
            // Editable override: a typed-in model not in the list is kept, never dropped.
            options.add(0, current);
        }

        String staticDefault = staticFallback == null || staticFallback.isEmpty() ? "" : staticFallback.get(0);
        String selection;
        if (!current.isEmpty()) {
            selection = current;
        } else if (!staticDefault.isEmpty() && options.contains(staticDefault)) {
            selection = staticDefault;
        } else {
            selection = options.isEmpty() ? "" : options.get(0);
        }
        return new Resolution(options, selection);
    }

    /** A dated snapshot suffix, as Anthropic lists models before the 4.6 generation ({@code -20250929}). */
    private static final java.util.regex.Pattern CLAUDE_SNAPSHOT = java.util.regex.Pattern.compile("-\\d{8}");

    /**
     * Whether the provider's own model list says the model is no longer offered to this key: true only for a
     * successful, complete, non-empty fetch that doesn't hold the model. A {@code models/} prefix is ignored, since
     * the plugin's Gemini ids carry none. For Claude, an alias counts as listed when a dated snapshot of it is
     * ({@code claude-sonnet-4-5} by {@code claude-sonnet-4-5-20250929}), because Anthropic lists those models only
     * by their dated id. The static fallback list is never passed here.
     */
    public static boolean notListed(AiProvider provider, String model, List<String> fetched, boolean fetchOk,
                                    boolean complete) {
        if (!fetchOk || !complete || fetched == null || fetched.isEmpty() || model == null) {
            return false;
        }
        String id = stripModelsPrefix(model.trim());
        if (id.isEmpty()) {
            return false;
        }
        for (String listed : fetched) {
            String candidate = stripModelsPrefix(listed);
            if (candidate.equals(id)) {
                return false;
            }
            if (provider == AiProvider.CLAUDE && candidate.startsWith(id)
                    && CLAUDE_SNAPSHOT.matcher(candidate.substring(id.length())).matches()) {
                return false;
            }
        }
        return true;
    }

    private static String stripModelsPrefix(String id) {
        return id.startsWith("models/") ? id.substring("models/".length()) : id;
    }
}
