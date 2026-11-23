# Testing and acceptance

> **Translated from:** `docs/TESTING.md` at `main` `59dd26a4`, 2026-11-23. Russian is canonical.

## 1. What runs in CI

CI runs on every pull request and on every push to `main`. A green run on the exact HEAD is the basis of acceptance.

| Step | What it proves |
|---|---|
| `python3 -m unittest discover -s tools -p 'test_*.py'` | Mock WebDAV behaviour, contract checks, tooling self-tests |
| Gradle wrapper validation | `gradle-wrapper.jar` is not substituted |
| `gradle :app:assembleDebug testDebugUnitTest lintDebug` | Build, JUnit across all modules, Android Lint |
| `gradle :core:ui:verifyRoborazziDebug :app:verifyRoborazziDebug` | Screenshot goldens match, both themes |
| Room schema check | Exported schemas committed and consistent |
| GMS scan (`tools/check_no_gms.py`) | No Google Play Services, Firebase or telemetry markers |
| detekt + ktlint | Static analysis; baseline may not grow |
| ADR index check | Every ADR file listed in `docs/DECISIONS/README.md` |
| Design sync check | Resources in the app match `design/` |
| Release APK build (R-A1/R-A2) | R8 minification and resource shrinking actually run |

**Toolchain:** Gradle 9.9.1, AGP 9.5.1 with built-in Kotlin 2.4.21, compileSdk 37, targetSdk 36, minSdk 26.

## 2. Acceptance model

**Acceptance is by automated checks** — [ADR-0104](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0104-auto-acceptance.md), owner decision 2026-11-21.

A scenario is accepted when:
1. the PR is merged into `main`;
2. CI on the exact merged HEAD is green (build, JUnit, lint, Room schema, GMS scan, detekt, Roborazzi);
3. independent review has passed.

**There is no "verified on the phone" status.** Planned manual verification is not part of acceptance. A defect found on a physical device is filed as a separate Issue labelled `bug` and fixed through the normal flow.

### What device checks are for

Device checks are a **diagnostic tool**, not an acceptance stage. With a phone attached over ADB an agent may:

- install a signed `app.enotone.android.preview` **over** the previous build — do not uninstall, user data must survive; `.preview.rN` builds are not used for this;
- capture `logcat` and screenshots to investigate a specific defect;
- reproduce a user-reported problem.

Do **not** run a full re-index or a real sign-out just to check the UI — only with explicit owner approval.

### Reporting a defect

Report a glitch → an Issue is filed with label `bug`, links to affected scenarios and normal priority. The fix goes through the normal flow: branch, tests, exact-HEAD CI, review, merge. This does not retroactively revoke acceptance of already-merged scenarios.

### Current device baseline

A phone without Google services, Android 12, running signed Stable Preview `app.enotone.android.preview`. Exact build number and revision are recorded in the diagnostics report for each run.

**Method for binding a build to a commit** (three independent proofs):
1. revision from `META-INF/version-control-info.textproto` inside the installed APK;
2. APK certificate SHA-256 matches the pinned `ENOTONE_PREVIEW_CERT_SHA256`;
3. the CI run number for that exact commit.

## 3. Test layers

| Layer | Where | Purpose |
|---|---|---|
| JVM unit tests | `*/src/test` | Pure logic, planners, decision tables — no Android |
| Robolectric | `app/src/test`, `playback/src/test` | Android APIs without a device: Room, services, shared prefs |
| Compose UI tests | `app/src/test` with `createComposeRule` | Screen behaviour, gestures, accessibility semantics |
| Roborazzi | `core/ui`, `app` | Screenshot goldens in both themes |
| Static contracts | `tools/test_*.py` | Invariants better checked by grep than by a runtime test (legacy, being replaced) |

**Rule:** new behaviour gets a test that fails if the behaviour breaks. Testing that a string exists in source code is discouraged — test behaviour instead (JUnit / Robolectric / Compose UI).

## 4. Running checks locally

```bash
./gradlew :app:assembleDebug testDebugUnitTest lintDebug detekt
./gradlew :core:ui:verifyRoborazziDebug :app:verifyRoborazziDebug
python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/check_adr_index.py
python3 design/tools/sync_android.py --check
```

On Windows use `gradlew.bat`. Full local run takes roughly 3–5 minutes on a warm daemon.

### Known local gotchas

| Symptom | Cause | Fix |
|---|---|---|
| `FileNotFoundException ... .lck` | Another Gradle daemon holds the lock, or a filesystem sandbox blocks it | Stop daemons, or run with full file access |
| `BindException: Cannot assign requested address` | VPN intercepting loopback | Disable VPN |
| Robolectric tests fail with `MavenArtifactFetcher` / `IOException` | Robolectric cannot download its `android-all` jar | Re-run; if CI-only, it is an infrastructure flake, not a code defect |
| Golden PNG mismatch after a UI change | Screenshots not updated | `./gradlew :app:recordRoborazziDebug`, review the diff visually |

## 5. Distinguishing infrastructure failures from real ones

CI occasionally fails while resolving dependencies (Robolectric's `nativeruntime-dist-compat`, Maven Central timeouts). The signature is distinctive:

- failure appears at the **dependency resolution** stage, before any test runs;
- the error mentions `Could not resolve` / `Could not download`;
- the same commit passes locally.

A real failure names a specific test and an assertion. When in doubt: re-run, and compare against a local run of the same commit.

## 6. Windows specifics

Line endings are canonical LF enforced by `.gitattributes` ([ADR-0096](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0096-canonical-line-endings.md)). The migration test helper has a Windows-specific workaround ([ADR-0097](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0097-migration-test-helper-windows.md)).

## See also

- [SCENARIOS](../SCENARIOS.md) (Russian) — the 136-scenario catalogue with per-item results
- [QUEUE](QUEUE.md) — what to work on next
- Russian canonical: [`docs/TESTING.md`](../TESTING.md)
