## 1. ConfigService thread safety

- [x] 1.1 Make `ConfigService.config` field volatile and add double-checked locking to `getConfig()`

## 2. ReadAction wrappers

- [x] 2.1 Wrap `SpecParsingService.parseSpec()` VirtualFile read in `ReadAction.compute()` with test fallback
- [x] 2.2 Wrap `ChangeService.getChangesFromDir()` VirtualFile `getInputStream()` in `ReadAction.compute()` with test fallback
- [x] 2.3 Wrap `ConfigService.reload()` VirtualFile `getInputStream()` in `ReadAction.compute()` with test fallback
- [x] 2.4 Wrap `TrackingMetadataWriter.readYaml()` VirtualFile read in `ReadAction.compute()` with test fallback
- [x] 2.5 Wrap `BuiltInValidator.readFile()` VirtualFile read in `ReadAction.compute()` with test fallback

## 3. Verify

- [x] 3.1 Run full test suite to confirm no regressions
