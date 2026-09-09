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
 * (prepended) rather than silently replaced (the editable-override guarantee).
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

        String selection = !current.isEmpty()
                ? current
                : (options.isEmpty() ? "" : options.get(0));
        return new Resolution(options, selection);
    }
}
