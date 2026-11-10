# ADR-0053 — Music-first interface, diagnostics behind an explicit action

Date: 2026-11-10. Status: Draft pending exact-HEAD CI and Huawei acceptance.

## Motivation
The owner accepted Stable Preview #227 but observes that trial/probe interfaces, status reports, and an expanded player fill the screen so aggressively that actual tracks and albums are barely visible. Fix layout and discoverability before implementing more media features. **This is functional Material 3 cleanup, not approval of the enoTone palette or future design assets.**

## Decisions
1. **Open the library after Keystore credential restoration**. Fresh installs (no restored login) retain a usable connection screen; entering email and password must never hide the fields in the middle of typing. Back returns to the home/maintenance screen.
2. Home shows library entry, collapsible connection settings, **incremental /AllMusic catalogue refresh** and the already accepted continuous index action. Detailed counters are collapsed by default with a short summary. The bounded four-batch run and all test/probe/Room tag-audit/pilot controls remain under **Technical tools and diagnostics**, collapsed by default.
3. Inside the library, reserve screen height for **tabs, search and a scrolling LazyColumn of tracks/albums/artists**. Move the huge playback control panel into an optional **scrollable full-player dialog**. When a track is selected, keep a small two-row **mini player beneath the list**, with track title, state, Pause/Resume, Next and Player button. Retain seek, repeat, shuffle, cache, handoff and Media3 queue logic. Advanced telemetry is gated by the original Diagnostics toggle within the full player.
4. Shorten library header: Back + section title + page-settings action in one row. Do not display the long technical explanation of grouped tags on every list page. Unknown Album and independent Artist/Album Artist navigation remain unchanged.
5. No changes to Room version/3590 tag rows, WebDAV network budget, credentials/Keystore, background indexer, playback service, signing, application ID or colors. No network requests merely from opening or switching tabs.

## Validation
Exact-HEAD CI: UI contract for library-first restoration and retained service controls, compact player after LazyColumn, full player with seek/diagnostics, RU/EN resources; Android Build/JUnit/Lint, Room, Mock WebDAV, no-GMS, stable Preview identity gate. Main signed Preview must install **over #227 without uninstall**.

Huawei smoke: saved credentials open library automatically; browse all four pages including Unknown Album with list visible. Select a track, verify mini player, Pause/Resume and Next; expand full player and check seek/repeat/shuffle then close without stopping music. From Back open technical tools and verify import/scan/audit controls remain. On first-time login both credential fields must remain visible during typing. No full reindex for this check.
