package com.johnnyblabs.openspec.ai;

import com.intellij.util.messages.Topic;

/**
 * Fired when the Direct API configuration (provider / key / model) changes at runtime — e.g. the
 * Settings dialog is applied or the Setup Wizard persists. Open tool-window surfaces subscribe so
 * they can re-offer or withdraw the Direct API delivery option (and its model selector) immediately,
 * instead of only on the next unrelated refresh or an IDE restart.
 *
 * <p>Project-scoped: a Direct API key is app-global in the credential store, but the settings that
 * gate the UI ({@code aiProvider}, {@code preferredDeliveryMethod}) live on the project-level
 * {@link com.johnnyblabs.openspec.settings.OpenSpecSettings}, and the subscriber is the per-project
 * tool window — so the project message bus is the correct scope.
 */
public interface AiConfigListener {

    Topic<AiConfigListener> TOPIC =
            new Topic<>("OpenSpec AI configuration changed", AiConfigListener.class);

    /** The AI/Direct-API configuration was committed; re-evaluate delivery options. */
    void aiConfigChanged();
}
