# STATUS — current state

> **Translated from:** `docs/STATUS.md` at `main` `59dd26a4`, 2026-11-23. Russian is canonical.
> **Order of work: [QUEUE](QUEUE.md)** — it takes precedence over any list elsewhere.

A short snapshot, not a journal and not a task queue. History — [CHANGELOG](https://github.com/metravolokos/enoTone-android/blob/main/CHANGELOG.md); task status — GitHub Issues per [WORKFLOW](WORKFLOW.md); known limitations — [KNOWN_ISSUES](KNOWN_ISSUES.md); decisions — `docs/CURRENT_DECISIONS.md` (Russian).

**Snapshot:** 2026-11-23 · `main` `59dd26a4` · version `0.6.2`.

## Implemented in `main`

- **Library.** WebDAV to Cloud.Mail.ru, flat `/AllMusic`, Room v7, incremental background indexing of MP3/FLAC tags, permanent WebP artwork masters up to 1200 px.
- **Playback and queue.** Media3 / MediaSessionService, queue and position restore across process death, shuffle/repeat, queue editing and playback-order view. Active queue is capped at 100 tracks with safe restore of longer legacy checkpoints ([ADR-0099](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0099-queue-limit-100.md)).
- **Interface.** Android v2 design, navigation shell with floating bottom bar and mini-player, Graphite and Porcelain themes, single fade-through page transition and a vertical sheet for the full player. **Fully translated into Russian and English** — 272 strings, 14 plurals, full parity between `values/` and `values-en/`.
- **Sign-in.** Credentials permanently stored in Android Keystore (AES-GCM). Sign-out deletes account data before the Keystore is cleared ([ADR-0080](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0080-permanent-login-sign-out.md)).
- **Quality.** CI runs build, JUnit across all modules, lint, detekt, Room schema check, GMS scan, Roborazzi screenshot comparison, ADR index check, design-resource sync, and a minified release APK build. Toolchain: Gradle 9.9.1, AGP 9.5.1, compileSdk 37, targetSdk 36, minSdk 26 ([ADR-0100](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0100-gradle9-agp9-compilesdk37.md)).

## Acceptance

**By automated checks only** ([ADR-0104](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0104-auto-acceptance.md)). A scenario is accepted when the PR is merged, CI on the exact merged HEAD is green, and independent review has passed. There is no "verified on device" status.

Device checks remain a diagnostic tool: with a phone attached over ADB an agent can reproduce a defect, capture logcat and screenshots. A defect found that way becomes a separate Issue labelled `bug`.

## Recent work

The project went through a full review pass on 2026-11-22 — see [DEVLOG](../DEVLOG.md) (Russian) for the reasoning:

- code and product audits by an external model produced 11 issues, grouped into three waves in [QUEUE](QUEUE.md);
- 191 stale branches archived as tags and deleted from the remote;
- four outdated documentation files rewritten;
- three defects fixed from the code audit;
- four product defects fixed, including a regression found only by on-device diagnostics.

## Open work

See [QUEUE](QUEUE.md) — 11 open issues across three waves, plus two questions awaiting an owner decision.

## See also

- [KNOWN_ISSUES](KNOWN_ISSUES.md) — what does not work or is deliberately limited
- [ARCHITECTURE](ARCHITECTURE.md) — how the app is built
- Russian canonical: [`docs/STATUS.md`](../STATUS.md)
