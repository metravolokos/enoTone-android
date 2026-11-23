# Module map — where each concern lives

> **Translated from:** `docs/MODULE_MAP.md` at `main` `59dd26a4`, 2026-11-23. Russian is canonical.

Use this to jump straight to the code for a concern instead of searching the tree.

| Concern | Where |
|---|---|
| App entry, Activity, app container (manual DI) | `app/src/main/java/app/enotone/android/MainActivity.kt`, `AppContainer.kt` |
| Navigation shell, tab switching, detail stack | `app/.../AppNavigator.kt`, `AppShell.kt`, `AppMotionRoot.kt` |
| Account lifecycle, sign-in, sign-out, reauthentication | `app/.../AccountSettingsViewModel.kt`, `AndroidAccountSignOutPort.kt`, `core/model/.../AccountSignOut.kt`, `AccountSession.kt` |
| Credential storage (Keystore AES-GCM) | `app/.../LocalCredentialVault.kt`, `CredentialEnvelope.kt` |
| Legacy sign-out recovery | `app/.../LegacySignOutRecovery.kt`, `SignOutMarker` |
| Library screens (tracks, albums, artists) | `feature/library/`, `app/.../LibraryScreenContent.kt`, `OfflineLibraryScreen.kt` |
| Library state and loading | `app/.../LibraryViewModel.kt`, `data/library/.../OfflineTrackCatalog.kt`, `OfflineLibraryGrouping.kt` |
| Search (in-memory, Russian sort) | `app/.../LibraryScreenContent.kt` (field), normalised keys in `data/library/` |
| Tag parsing (MP3/FLAC) | `data/library/.../FastAudioTagReader.kt`, `EmbeddedArtworkPayloadReader.kt` |
| Indexing sessions and limits | `data/library/.../ManagedTagIndexSession.kt`, `ParallelTagIndexer.kt`, `app/.../IndexingRunner.kt`, `IndexingForegroundService.kt` |
| Artwork: fetch, cache, masters, refs | `app/.../ArtworkPreviewRepository.kt`, `AccountArtworkFileCleanup.kt`, `AlbumArtworkCatalog.kt` |
| Now Playing screen, player UI | `feature/player/`, `app/.../PlayerViewModel.kt` |
| Queue: playback, edit, persistence | `playback/.../PreviewAudioPlayback.kt`, `QueueEditPlanner.kt`, `QueueStateFileStore.kt`, `PersistedQueueState.kt` |
| Playback service (MediaSession) | `playback/.../EnoTonePlaybackService.kt` |
| Streaming over WebDAV | `playback/.../WebDavMedia3DataSource.kt`, `core/network/.../WebDavRangeReader.kt` |
| Transient audio cache | `playback/.../TransientMediaCache.kt`, `TransientCachePolicy.kt` |
| Network availability signal | `core/network/.../NetworkMonitor.kt` |
| Database schema and migrations | `core/database/.../EnoToneDatabase.kt`, `schemas/` |
| Design system (theme, colours, components) | `core/ui/` |
| Design sources and generators | `design/` — see [`design/AGENTS.md`](../../design/AGENTS.md) |
| Settings and diagnostics | `feature/settings/`, `app/.../SettingsScreen.kt`, `SettingsDiagnosticsScreen.kt` |

## Build and tooling

| Concern | Where |
|---|---|
| Dependency versions | `gradle/libs.versions.toml` |
| App build config, signing, versioning | `app/build.gradle.kts` |
| CI pipeline | `.github/workflows/ci.yml` |
| Static checks (GMS, ADR index, detekt baseline, design sync) | `tools/` |
| Detekt configuration and baselines | `config/detekt/detekt.yml`, `*/detekt-baseline.xml` |
