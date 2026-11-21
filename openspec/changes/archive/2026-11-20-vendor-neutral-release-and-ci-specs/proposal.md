## Why

The public specs for release preparation and CI name specific hosting and tracking services. Published artifacts should describe behavior in neutral terms, so the specs do not expose internal tooling or tie the requirements to one vendor.

## What Changes

- The release-prep requirements for tracker state and milestone or cycle creation refer to the issue tracker and the work-item tracker generically.
- The CI requirement for concurrency in self-hosted workflows refers to the self-hosted build workflow and runner, not to a named service.
- The requirement "Testable functionality is unit-tested" is renamed "Functionality without the IDE platform is unit-tested", because one of its scenario titles named a vendor and scenario titles cannot change without a rename. Its text and other scenarios are unchanged.

## Capabilities

### Modified Capabilities

- `release-prep`: tracker state validation and milestone or cycle auto-creation use neutral wording.
- `ci`: the concurrency scenario and one requirement name use neutral wording.

## Impact

- No behavior change. The requirements check the same things in the same way.
- The Purpose text of `release-prep` is not covered by this change, because deltas cannot edit it.
