## 1. Apply prompt assembly

- [x] 1.1 Create `ApplyPromptBuilder` utility class that reads design.md, specs files, and tasks.md from a change directory and assembles a full-context implementation prompt
- [x] 1.2 Include `# Change: <name>` header, design context, spec context, full task list, and implementation instruction in the assembled prompt
- [x] 1.3 Add save-path hint line for CLI-based tools (append tasks.md path to prompt)

## 2. Rewrite Apply action

- [x] 2.1 Rewrite `OpenSpecApplyAction` to check artifact completion status before proceeding (warn if artifacts are incomplete)
- [x] 2.2 Wire Apply action to use `ApplyPromptBuilder` and deliver via the workflow panel's tool selector delivery mechanism (clipboard, editor tab, or API)
- [x] 2.3 Handle edge cases: no active change, all tasks already complete, no tasks.md file

## 3. Workflow panel Apply button

- [x] 3.1 Add "Apply Tasks" button to `WorkflowActionPanel` that appears when all artifacts are done and incomplete tasks remain
- [x] 3.2 Add task progress indicator (e.g., "3/12 tasks complete") to the panel when in apply-ready state
- [x] 3.3 Add inline hint when 10+ tasks remain: "Large task list — consider reviewing tasks.md first"
- [x] 3.4 Wire Apply button click to assemble prompt and deliver via selected tool/delivery method

## 4. Post-Apply watching state

- [x] 4.1 After Apply delivery, show watching state with "Watching for task progress..." message and tool-specific instructions
- [x] 4.2 Register file watcher on tasks.md to detect checkbox changes and update task progress display
- [x] 4.3 Add "Check progress" button as manual fallback for re-parsing tasks.md
- [x] 4.4 Dismiss watching state and show "All complete" with archive guidance when all tasks are marked done

## 5. Verify

- [x] 5.1 Run `./gradlew clean build test` — all green
- [x] 5.2 Verify Apply button appears in workflow panel after all artifacts are generated
- [x] 5.3 Verify prompt assembly includes design, specs, and tasks content
- [x] 5.4 Verify task progress updates when tasks.md is modified externally
