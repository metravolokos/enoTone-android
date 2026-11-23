# Architecture — enoTone Android

> **Translated from:** `docs/ARCHITECTURE.md` at `main` `92348b9f`, 2026-11-23.
> The Russian document is canonical. If they disagree, Russian wins.

## Overview

enoTone is a native Android audio player for personal music stored in Cloud.Mail.ru, accessed over HTTPS WebDAV. The verified layout is a flat `/AllMusic` folder. No Google Play Services, no Firebase, no telemetry.

Single-activity Compose app with a process-scoped service layer. Media3 owns playback; Room owns the local catalogue; the filesystem owns artwork and queue checkpoints.

## Modules

The project is a multi-module Gradle build (Kotlin DSL). Module boundaries exist to keep compile times down and to make ownership obvious — not for the sake of modularity.

| Module | Responsibility |
|---|---|
| `:app` | Single Activity, navigation shell, ViewModels that bind features to the data layer, account lifecycle |
| `:core:model` | Plain Kotlin models and ports with no Android dependencies (`AccountSignOutPort`, `AccountSession`) |
| `:core:database` | Room entities, DAOs, migrations, exported schemas |
| `:core:network` | WebDAV client, `NetworkMonitor`, HTTP ranges |
| `:core:ui` | Design system: theme, colours, typography, reusable components |
| `:data:library` | Catalogue loading, tag parsing, indexing sessions, grouping |
| `:data:playlist` | Playlist and favourites persistence |
| `:playback` | Media3/MediaSession service, queue, transient cache, persistence |
| `:feature:library` | Library screens: tracks, albums, artists, album artists |
| `:feature:player` | Now Playing screen, queue sheet, error sheet |
| `:feature:settings` | Settings, diagnostics sub-screen |

Dependency direction is one-way: `:feature:*` → `:data:*` → `:core:*`. Features never depend on each other; `:app` wires them.

## Layers

**Presentation.** Compose screens are stateless where possible; state comes from ViewModels as a single immutable `UiState`. This keeps screenshot tests honest: the same state produces the same pixels.

**Domain-ish.** `:core:model` holds decisions as pure functions with no Android imports. `LegacySignOutRecovery.decide()` and `AccountSignOut` are the clearest examples: they are testable on the JVM without Robolectric.

**Data.** Room is the single source of truth for the catalogue. The filesystem holds artwork masters and queue checkpoints; both are addressed by hashes, never by user-visible names.

**Playback.** `:playback` owns `EnoTonePlaybackService` (a `MediaSessionService`). It survives the UI: closing the app does not stop playback, and the queue is persisted in a layout+position pair so a torn write falls back to a consistent previous state.

## Data flow

```
WebDAV (Cloud.Mail.ru)
    │  PROPFIND Depth:1, Range GET
    ▼
:core:network  ──►  :data:library  ──►  :core:database (Room)
                          │                      │
                          │  tags, grouping      │  catalogue
                          ▼                      ▼
                    :feature:library      :app ViewModels
                                                 │
                                                 ▼
                                          Compose screens
```

Playback has its own path: `:app` asks `:playback` to play a queue; Media3 streams through a `DataSource` that issues HTTP range requests, optionally backed by a transient LRU cache. Artwork is fetched separately and cached permanently on disk.

## Concurrency

- **Main thread** — Compose, ViewModel state updates, service callbacks.
- **IO pool** — Room, filesystem, network setup. Never blocked from main.
- **Media3 internal** — playback and buffering.

Service work that must not block the main thread is submitted to an ordered IO executor. Data structures shared between threads use `AtomicBoolean`/`AtomicLong` and immutable snapshots rather than locks.

## Where the hard rules live

| Rule | Enforced by |
|---|---|
| No GMS / Firebase / telemetry | `tools/check_no_gms.py` in CI |
| HTTPS only, same origin, no redirects | `WebDavMedia3DataSource`, `WebDavRangeReader` |
| Credentials never in Git/logs/Room/Intents | `LocalCredentialVault`, contract tests |
| Artwork masters ≤1200 px, permanent, no auto-eviction | `ArtworkPreviewRepository` ([ADR-0067](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0067-permanent-album-artwork.md)) |
| Room migrations never lose data | Exported schemas + migration tests |
| Sign-out deletes account data before Keystore | `AccountSignOut` step order ([ADR-0080](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0080-permanent-login-sign-out.md)) |

## What is deliberately absent

No analytics, no crash reporting SDK, no remote config, no push. The app talks to exactly two places: the user's WebDAV and (if enabled) an AI endpoint the user configures. Everything else is local.

## See also

- [MODULE_MAP](MODULE_MAP.md) — where a specific concern lives
- [TESTING](TESTING.md) — how this architecture is verified
- Russian canonical: [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md)
