## 1. GitHub Actions Workflow

- [x] 1.1 Create `.github/workflows/build.yml` with two parallel jobs: `build` and `verify`
- [x] 1.2 Configure `build` job: checkout, `actions/setup-java@v4` (Temurin 21), Gradle cache, `./gradlew build`, upload test results
- [x] 1.3 Configure `verify` job: checkout, setup-java, Gradle cache, `./gradlew verifyPlugin`
- [x] 1.4 Add signing steps to `build` job (main branch only): `./gradlew signPlugin`, `./gradlew verifyPluginSignature`, upload signed artifact
- [x] 1.5 Ensure workflow uses `./gradlew` (wrapper) not system `gradle`

## 2. Gradle Wrapper

- [x] 2.1 Verify `gradle/wrapper/gradle-wrapper.properties` exists and points to Gradle 9.0.0
- [x] 2.2 Verify `gradlew` and `gradlew.bat` are committed and executable
- [x] 2.3 Test `./gradlew build` runs successfully locally

## 3. GitHub Repository Secrets (manual)

- [ ] 3.1 Add `PLUGIN_SIGNING_KEY` to GitHub repo secrets (same base64 value as Forgejo)
- [ ] 3.2 Add `PLUGIN_SIGNING_CERTIFICATE` to GitHub repo secrets
- [ ] 3.3 Add `PLUGIN_SIGNING_KEY_PASSWORD` to GitHub repo secrets

## 4. README Badge

- [x] 4.1 Add GitHub Actions build status badge to `README.md`

## 5. Verification

- [ ] 5.1 Push to GitHub and verify both jobs run and pass
- [ ] 5.2 Verify Gradle cache works (check for cache hit on second run)
- [ ] 5.3 Verify signing works on main branch push
