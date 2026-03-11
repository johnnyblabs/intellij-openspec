package com.johnnyb.openspec.tracking;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.jetbrains.annotations.Nullable;

public final class TrackerCredentialStore {

    private static final String SERVICE_PREFIX = "OpenSpec-Tracker-";

    private TrackerCredentialStore() {
    }

    public static void storeToken(TrackerType tracker, String token) {
        CredentialAttributes attributes = createAttributes(tracker);
        PasswordSafe.getInstance().set(attributes, new Credentials(tracker.name(), token));
    }

    @Nullable
    public static String getToken(TrackerType tracker) {
        CredentialAttributes attributes = createAttributes(tracker);
        Credentials credentials = PasswordSafe.getInstance().get(attributes);
        return credentials != null ? credentials.getPasswordAsString() : null;
    }

    public static void removeToken(TrackerType tracker) {
        CredentialAttributes attributes = createAttributes(tracker);
        PasswordSafe.getInstance().set(attributes, null);
    }

    public static boolean hasToken(TrackerType tracker) {
        String token = getToken(tracker);
        return token != null && !token.isBlank();
    }

    private static CredentialAttributes createAttributes(TrackerType tracker) {
        return new CredentialAttributes(
                CredentialAttributesKt.generateServiceName(SERVICE_PREFIX, tracker.name()));
    }
}
