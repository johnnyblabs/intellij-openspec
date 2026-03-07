package com.johnnyb.openspec.actions;

import com.intellij.openapi.actionSystem.DataKey;

public final class OpenSpecDataKeys {
    public static final DataKey<String> CHANGE_NAME = DataKey.create("OpenSpec.ChangeName");
    public static final DataKey<String> ARTIFACT_ID = DataKey.create("OpenSpec.ArtifactId");

    private OpenSpecDataKeys() {
    }
}
