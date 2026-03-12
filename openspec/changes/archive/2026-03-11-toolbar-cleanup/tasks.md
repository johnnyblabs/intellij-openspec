## 1. Remove Change-Scoped Actions from Toolbar

- [x] 1.1 In `plugin.xml`, remove the `<separator/>` line from the `OpenSpec.ToolWindowToolbar` group.
- [x] 1.2 In `plugin.xml`, remove the `<reference ref="OpenSpec.Apply"/>` line from the `OpenSpec.ToolWindowToolbar` group.
- [x] 1.3 In `plugin.xml`, remove the `<reference ref="OpenSpec.Archive"/>` line from the `OpenSpec.ToolWindowToolbar` group.

## 2. Update Toolbar Icons

- [x] 2.1 In `plugin.xml`, add `icon="AllIcons.Actions.Refresh"` to the `OpenSpec.Refresh` action definition.
- [x] 2.2 In `plugin.xml`, change the `OpenSpec.Propose` action icon from `icon="/icons/change.svg"` to `icon="AllIcons.General.Add"`.

## 3. Verify

- [x] 3.1 Run `./gradlew compileJava` and confirm clean compilation.
- [x] 3.2 Run `./gradlew clean test` and confirm all tests pass.
