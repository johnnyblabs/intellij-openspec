## 1. Runner Setup

- [ ] 1.1 Install Qodana CLI on the `java-21` runner Docker image
- [ ] 1.2 Verify `qodana scan --help` works on the runner

## 2. Configuration

- [x] 2.1 Create `qodana.yaml` at project root with `qodana-jvm` linter and source scope `src/main/java`
- [ ] 2.2 Run Qodana locally to generate the initial baseline file
- [ ] 2.3 Commit the baseline file to the repository

## 3. CI Integration

- [x] 3.1 Add `qodana` job to `.forgejo/workflows/build.yaml` — runs on PRs targeting main, no dependency on build job
- [x] 3.2 Configure the job to run `qodana scan` with the baseline and fail on new issues
- [x] 3.3 Upload Qodana results as a CI artifact for review

## 4. Verification

- [ ] 4.1 Open a test PR and confirm Qodana job runs and passes (no new issues)
- [ ] 4.2 Introduce a deliberate issue and confirm the job fails
