## 1. OpenSpec YAML File Type

- [x] 1.1 Create `OpenSpecYamlFileType` class extending `LanguageFileType` with YAML language, `.openspec.yaml` default extension, and OpenSpec icon
- [x] 1.2 Register the file type in `plugin.xml` with `<fileType>` extension using `fileNames=".openspec.yaml"`

## 2. Spec File IconProvider

- [x] 2.1 Create `OpenSpecIconProvider` extending `IconProvider` that returns spec icon for `spec.md` under `openspec/specs/` and delta-spec icon for `spec.md` under `openspec/changes/*/specs/`
- [x] 2.2 Register the `IconProvider` in `plugin.xml` with `<iconProvider>` extension

## 3. Verification

- [x] 3.1 Build the plugin and verify `.openspec.yaml` shows OpenSpec icon in project tree and editor tabs
- [x] 3.2 Verify `spec.md` files under `openspec/specs/` show spec icon in project tree
- [x] 3.3 Verify delta spec files under `openspec/changes/*/specs/` show delta-spec icon in project tree
- [x] 3.4 Verify `spec.md` files outside `openspec/` are unaffected
