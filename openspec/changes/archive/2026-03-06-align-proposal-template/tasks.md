## 1. Update Template

- [x] 1.1 Rewrite `TemplateProvider.proposalTemplate()` to match the official OpenSpec 1.2.0 spec-driven template (Why, What Changes, Capabilities with New/Modified subsections, Impact). Remove the `# Proposal:` H1 heading. Change parameters to `(String changeName, String why, String whatChanges)` where `why` and `whatChanges` may be null/blank — use HTML comment placeholders when blank.

## 2. Update Dialog

- [x] 2.1 In `ProposeChangeDialog.java`, replace "Description:" `JBTextField` with "Why:" and "What Changes:" `JBTextArea` fields wrapped in `JBScrollPane`
- [x] 2.2 Remove the validation requirement for description — only "Change name" is required
- [x] 2.3 Add `getWhy()` and `getWhatChanges()` accessor methods, remove `getDescription()`

## 3. Update Callers

- [x] 3.1 Update `ScaffoldingService.createChange()` — change `description` parameter to `why` and add `whatChanges` parameter, pass both to `TemplateProvider.proposalTemplate()`
- [x] 3.2 Update `OpenSpecProposeAction` to call `dialog.getWhy()` and `dialog.getWhatChanges()` and pass them through to `ScaffoldingService`

## 4. Verify

- [x] 4.1 Build the project and confirm no compilation errors
- [x] 4.2 Verify the generated proposal.md matches the official template when all fields are provided
- [x] 4.3 Verify the generated proposal.md uses placeholders when optional fields are blank
