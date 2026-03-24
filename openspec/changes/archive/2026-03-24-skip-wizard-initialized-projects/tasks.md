## 1. Onboarding Fix

- [x] 1.1 Update `OpenSpecToolWindowFactory` to show tree view for all initialized states (not just READY)
- [x] 1.2 Only launch wizard for NOT_INITIALIZED state, auto-set setupCompleted for all initialized states

## 2. Icon Bar Redesign

- [x] 2.1 Add Apply icon button to icon bar, positioned first (before Compliance)
- [x] 2.2 Add Compliance icon button to icon bar, positioned after Apply
- [x] 2.3 Enable Apply and Compliance when all artifacts are complete, disabled otherwise
- [x] 2.4 Add contextual tooltips for Apply and Compliance buttons

## 3. Overflow Menu Cleanup

- [x] 3.1 Remove Apply Tasks from overflow (now in icon bar)
- [x] 3.2 Remove Compliance Check from overflow (now in icon bar)
- [x] 3.3 Remove Start New Change and Fast-Forward from overflow (not change-scoped)
- [x] 3.4 Remove Archive All Changes from overflow (not change-scoped)
- [x] 3.5 Overflow now contains only Sync Specs and conditional Cancel Generation

## 4. Testing

- [x] 4.1 Unit tests for wizard decision logic covering all four states
- [x] 4.2 Unit tests for tree vs Getting Started content selection
