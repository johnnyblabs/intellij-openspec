## 1. Spec

- [x] 1.1 Reword the `[~]` clause in the Pre-archive verification requirement so open tasks are not a block.
- [x] 1.2 Validate the change strictly.

## 2. Panel

- [x] 2.1 Add a helper that decides whether the Archive button is enabled, and make the panel use it.
- [x] 2.2 With tasks remaining, route Archive through the Verify action instead of archiving directly.
- [x] 2.3 Update the tooltip so it says tasks remain and that Verify comes first.

## 3. Tests

- [x] 3.1 Replace the tests that mirrored the old formula with tests of the helper.
- [x] 3.2 The full test suite passes.
