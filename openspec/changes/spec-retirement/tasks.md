# Tasks

## 1. Captures

- [ ] 1.1 `scripts/capture-spec-retirement.py`: real `openspec archive` runs, isolated, for retire-clean, retire-folder-with-other-file, without-marker, without-marker-with-leftovers, extra-section, prose-after-scenario, `<args>`-prose, html-block, table-row, plus-bullets/indented-continuation, fenced-heading and unhonorable-marker; output, exit, and the tree after. On 1.7.0, 1.8.0, 1.11.0, 1.16.1 and 2.2.0.

## 2. Merge

- [ ] 2.1 Read `retire_capabilities` as upstream does (D1).
- [ ] 2.2 Port the check, variants A, B and C, chosen by the followed release (D2).
- [ ] 2.3 Retire or refuse in the release's words (D1, D3); below 1.8 never retire.
- [ ] 2.4 `SpecSyncResult.retired`; `applySync` deletes and prunes in one write action (D4).

## 3. UI

- [ ] 3.1 Sync preview: the retire tab, banner and empty side; refusals as before (D5).
- [ ] 3.2 Archive prompt names the specs a retirement deletes (D5).
- [ ] 3.3 Result: "Retired: <ids>" and the restore hint, on the plugin path and from the CLI's "Retiring" lines (D5).

## 4. Tests

- [ ] 4.1 Contract tests: every captured case on every captured release; the plugin's outcome (retired, refused with the CLI's text, or written) equals the CLI's, and the tree after matches.
- [ ] 4.2 `applySync` deletes and prunes, keeps a folder with other files, and writes nothing when any capability is refused.
- [ ] 4.3 The CLI "Retiring" line parser, against a captured run.
- [ ] 4.4 Preview and prompt: the retire tab and banner, the deletion line.
- [ ] 4.5 Mutations, each turning a test red: retiring without the marker; variant B's list rule on a 1.8 release; variant C's HTML rule on 2.1.0; deleting a folder that holds another file.

## 5. Docs and verification

- [ ] 5.1 Feature reference (Sync Specs, Archive) and a CHANGELOG entry.
- [ ] 5.2 `./gradlew build`, `openspec validate --all --strict`.
