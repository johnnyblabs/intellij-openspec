## 1. Workflow Changes

- [x] 1.1 Remove `if: github.ref == 'refs/heads/main'` condition from the `verify` job
- [x] 1.2 Remove `needs: build` dependency from the `verify` job so it runs in parallel
- [x] 1.3 Add checkout step to verify job (already present, verify it's correct without the build dependency)

## 2. Verification

- [ ] 2.1 Push to a feature branch and confirm both `build` and `verify` jobs run in parallel
- [ ] 2.2 Confirm verify job reports status on PRs (visible as a check)
