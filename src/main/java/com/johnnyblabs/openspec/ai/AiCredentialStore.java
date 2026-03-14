package com.johnnyblabs.openspec.ai;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.jetbrains.annotations.Nullable;

public final class AiCredentialStore {

    private static final String SERVICE_PREFIX = "OpenSpec-AI-";

    private AiCredentialStore() {
    }

    /**
     * Stores an API key for the given provider.
     */
    public static void storeApiKey(AiProvider provider, String apiKey) {
        CredentialAttributes attributes = createAttributes(provider);
        PasswordSafe.getInstance().set(attributes, new Credentials(provider.name(), apiKey));
    }

    /**
     * Retrieves the API key for the given provider.
     */
    @Nullable
    public static String getApiKey(AiProvider provider) {
        CredentialAttributes attributes = createAttributes(provider);
        Credentials credentials = PasswordSafe.getInstance().get(attributes);
        return credentials != null ? credentials.getPasswordAsString() : null;
    }

    /**
     * Removes the stored API key for the given provider.
     */
    public static void removeApiKey(AiProvider provider) {
        CredentialAttributes attributes = createAttributes(provider);
        PasswordSafe.getInstance().set(attributes, null);
    }

    /**
     * Checks if an API key is stored for the given provider.
     */
    public static boolean hasApiKey(AiProvider provider) {
        String key = getApiKey(provider);
        return key != null && !key.isBlank();
    }

    private static CredentialAttributes createAttributes(AiProvider provider) {
        return new CredentialAttributes(
                CredentialAttributesKt.generateServiceName(SERVICE_PREFIX, provider.name()));
    }
}
