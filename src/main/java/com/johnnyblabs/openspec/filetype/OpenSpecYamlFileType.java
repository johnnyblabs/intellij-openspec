package com.johnnyblabs.openspec.filetype;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLLanguage;

import javax.swing.*;

public final class OpenSpecYamlFileType extends LanguageFileType {

    public static final OpenSpecYamlFileType INSTANCE = new OpenSpecYamlFileType();

    private OpenSpecYamlFileType() {
        super(YAMLLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "OpenSpec YAML";
    }

    @Override
    public @NotNull String getDescription() {
        return "OpenSpec change metadata";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "yaml";
    }

    @Override
    public Icon getIcon() {
        return IconLoader.getIcon("/icons/openspec.svg", OpenSpecYamlFileType.class);
    }
}
