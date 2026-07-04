## 1. plugin-core spec — capability-preservation contract

- [x] 1.1 Apply the `plugin-core` delta: ADD "Supported CLI versions and capability preservation" (per-version, client-faithful; delta + changelog + test required for any change to a supported version's client-backed capability set; version-scoped self-retirement permitted but governed; per-version tests pin each version).

## 2. changelog-automation spec — disclosure requirement

- [x] 2.1 Apply the `changelog-automation` delta: ADD "User-facing capability changes are disclosed" (add/remove/materially-change a user-facing capability → changelog entry in the unreleased section; version-scoped retirements note their scope; internal refactors exempt).

## 3. Test — pin the declared supported-version floor

- [x] 3.1 Add a test pinning the declared CLI floor at exactly `1.3.0` via the floor-consuming behavior (`SchemaService.isSchemaSupported()`): `1.3.0` supported, `1.2.9` not, `1.4.1` supported. Concrete teeth for the plugin-core "floor is declared and pinned" scenario. Per-capability per-version behavior is already pinned by `CoordinationServiceWindowTest.perVersionBehaviorContract`.
- [x] 3.2 The test fails if the declared floor drifts up (raising past `1.3.0` fails `floorVersionIsSupported`) or down (lowering below `1.3.0` fails `justBelowFloorIsNotSupported`) — no vacuous assert.

## 4. Verification

- [x] 4.1 `openspec validate capability-preservation-governance --strict` passes.
- [x] 4.2 `./gradlew build` green (tests + JaCoCo floor), including the new floor-pin tests.

> Scope note: the optional coordination-surfaces cross-link (having its CLI-version contract reference the plugin-core rule as the specific case of the general one) was dropped to keep this change to two capabilities; it can be a trivial follow-up doc edit.
