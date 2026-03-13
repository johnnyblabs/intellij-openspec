## 1. Settings infrastructure

- [x] 1.1 Add `cliTimeoutSeconds` field with default 30 to `OpenSpecSettings.State`
- [x] 1.2 Add getter/setter to `OpenSpecSettings`
- [x] 1.3 Add `JSpinner` to `OpenSpecSettingsPanel` with 1-3600 range
- [x] 1.4 Wire `OpenSpecConfigurable` for `isModified`/`apply`/`reset`

## 2. CliRunner integration

- [x] 2.1 Update `CliRunner.run(Project, String...)` to read timeout from settings

## 3. Verify

- [x] 3.1 Run full test suite to confirm no regressions
