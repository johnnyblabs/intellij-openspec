## 1. Rename Package in Source Files

- [x] 1.1 Replace `com.johnnyb.openspec` with `com.johnnyblabs.openspec` in all `src/main/java/**/*.java` files (package declarations and imports)
- [x] 1.2 Move directory `src/main/java/com/johnnyb/` to `src/main/java/com/johnnyblabs/`
- [x] 1.3 Replace `com.johnnyb.openspec` with `com.johnnyblabs.openspec` in all `src/test/java/**/*.java` files
- [x] 1.4 Move directory `src/test/java/com/johnnyb/` to `src/test/java/com/johnnyblabs/`

## 2. Update Configuration Files

- [x] 2.1 Update `group` in `build.gradle.kts` from `com.johnnyb.openspec` to `com.johnnyblabs.openspec`
- [x] 2.2 Update `pluginConfiguration.id` in `build.gradle.kts` to `com.johnnyblabs.openspec`
- [x] 2.3 Update `<id>` in `plugin.xml` to `com.johnnyblabs.openspec`
- [x] 2.4 Update all fully-qualified class names in `plugin.xml` (services, actions, inspections, annotators, line markers)
- [x] 2.5 Update `<vendor>` name from `johnnyb` to `johnnyblabs` in `plugin.xml`
- [x] 2.6 Update `vendor.name` from `johnnyb` to `johnnyblabs` in `build.gradle.kts`

## 3. Verification

- [x] 3.1 Run `grep -r "com.johnnyb.openspec"` to confirm no remaining references in source
- [x] 3.2 Build the project (`./gradlew build`) and verify no compilation errors
- [x] 3.3 Run `./gradlew verifyPlugin` and confirm it passes
