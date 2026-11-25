# KNOWN ISSUES — current defects and limitations

> **Translated from:** `docs/KNOWN_ISSUES.md` at `main` `59dd26a4`, 2026-11-23. Russian is canonical: if they disagree, Russian wins.

**Snapshot:** 2026-11-23 · version `0.6.2` · `main` `59dd26a4`. Acceptance is by automated checks ([ADR-0104](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0104-auto-acceptance.md)). A defect found on a device becomes a separate Issue labelled `bug`.

Only defects confirmed on the current code belong here. Findings that need an owner decision or a new ADR are tracked in Issues, not in this list.

## Playback

| Problem | Consequence | Plan |
|---|---|---|
| The first queue after a service start may play without the transient cache: it is prepared in the background and picked up for the next queue (ADR-0094) | The first track after a service restart is fetched over the network; prefetch of the next track starts from the second queue | Revisit if it becomes noticeable: warm the cache on app start rather than in `playQueue` |
| File size and ETag are captured when the queue is created; response `416` is not handled | A file changed on the server will not play until the queue is recreated | Requires an ADR on the network layer |
| Tracks from the first import have no `firstSeenEpochMs` | They do not appear in "Recently added"; backfilling from `remoteModified` was not approved by the owner | Retro-fill decision — [Issue #201](https://github.com/metravolokos/enoTone-android/issues/201) |
| Switching Wi-Fi ↔ LTE during a track produces an error; a network signal exists (ADR-0102) but the player does not use it | Manual retry after switching networks | Separate task |
| The transient audio cache is wiped on service start; there is no pinning of current/next, only a 512 MiB LRU | Repeated traffic after a restart | Later, needs an ADR |
| `EnoTonePlaybackService` is `exported="false"` and there is no `MediaLibraryService` | External `MediaController` / Android Auto may fail to connect (unverified) | Later |
| Bluetooth MediaSession does not implement `onPlaybackResumption` after process death | A headset cannot auto-resume without opening the app | Separate PR |
| Catalog loading (`ensureCatalog`) has no retry of its own: a `repository.load()` failure publishes `NO_TRACKS_IN_ROOM`, and the next load is opportunistic only (re-verifying the same account, a track change or opening Now Playing through `loadArtwork`, account change, a new library revision, service reconnect). There is no timer, budget or backoff for catalog loading; the queue-restore budget does not cover it (owner decision on #254, Q4) | If the load fails at startup and the user performs none of those actions, the player catalog stays empty: the queue is not restored and the album and artwork are not shown | Separate Issue; this row is removed once `ensureCatalog` gets its own bounded retry (budget + backoff) proven by a behavioural reload test |
| The playback service keeps its binding while paused in the background: `isForeground=true`, `am stopservice` is rejected | The states that #258 and the #268 regression were written for are nearly unreachable in practice | See [#273](https://github.com/metravolokos/enoTone-android/issues/273) — awaiting an owner decision |
| Prefetch cancellation race in `TransientMediaCache.prefetchNext` | A download starts after the track changed or playback stopped | [#255](https://github.com/metravolokos/enoTone-android/issues/255) |
| `bindService` returned `true` but the service never connected: `ERROR` is shown, there is no re-bind, and `retryCurrent()` is useless when `service == null` | Dead end with no way out but restarting the app | [#258](https://github.com/metravolokos/enoTone-android/issues/258) |
| The `OPEN_NOW_PLAYING` extra does not open Now Playing on an already running activity without `NEW_TASK` / `singleTop` | External calls break; the notification itself sends correct flags, so the user path is unaffected | See [#274](https://github.com/metravolokos/enoTone-android/issues/274) |

## Data and privacy

| Problem | Consequence | Plan |
|---|---|---|
| Library import does not check `AccountSession.blocks()`: the `LibrarySnapshotStore.save()` transaction can commit after `deleteAccountData` and after the Keystore is cleared | The cleanup proof becomes false; the next launch falls into fail-closed. Data is **not** lost — the guard fails in the safe direction | Low priority: the window is narrow (tap Refresh and Sign out in a row), and fail-closed already prevents loss. Fix by checking `blocks()` in `LibraryIndexingGateway.update()` |
| When artwork masters are deleted, an unreadable `ref-*.txt` is silently treated as absent | A master referenced by another account can be deleted as an "orphan" — silent data loss with multiple accounts | Separate PR; does not occur with a single account ([AccountArtworkFileCleanup.kt:27-43](https://github.com/metravolokos/enoTone-android/blob/main/app/src/main/java/app/enotone/android/AccountArtworkFileCleanup.kt#L27-L43)) |
| Scoped sign-out with another account present cleans only the permanent artwork store; legacy `cacheDir/enotone-artwork-v2` survives, and `migrateLegacy()` copies the master back on the next request | An old artwork master returns after sign-out → sign-in without any network call. The `storedBytes() == 0` check counts only the permanent directory and cannot see the leftover | Separate PR; risk class R ([ArtworkPreviewRepository.kt:563-574](https://github.com/metravolokos/enoTone-android/blob/main/app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt#L563-L574)) |

## Data and network

| Problem | Consequence | Plan |
|---|---|---|
| Track ID is `SHA-256(source + encodedPath)` without encoding canonicalisation; a rename becomes a new track | Renaming does not carry a playlist entry over to the new ID | Limitation of ADR-0085 |
| FTS4 abandoned (#143, ADR-0091): the index is removed, Room v7, search runs over precomputed keys | No separate measurement of search latency ≤20 ms | Per [ADR-0104](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0104-auto-acceptance.md) acceptance does not require a measurement; a complaint becomes a separate Issue |
| Three HTTP stacks (Ktor CIO, `HttpURLConnection`, `HttpsURLConnection`) with no shared backoff with jitter | Duplicated logic; system proxy/VPN behaviour for Ktor CIO is unverified | Requires an ADR on a unified HTTP stack |
| Tags are read for MP3/FLAC only; ID3v2.3 tag-level unsync is deliberately rejected; MPEG-2/2.5 and hybrid unsync lack fixtures (#182) | Other formats and rare ID3 cases are only partially supported | Separate decision and tests ([#182](https://github.com/metravolokos/enoTone-android/issues/182)) |

## Architecture and quality

| Problem | Plan |
|---|---|
| Indexing-session limits (`SESSION_BATCH_LIMIT`, `SESSION_TIME_BUDGET`) make `IndexingRunner` report `COMPLETE`, so the first run shows "All done" while part of the library is unread. **The first run can show "All done" with unread tracks** | [#257](https://github.com/metravolokos/enoTone-android/issues/257) |
| `LibraryDataRevision` counts only `savedWithTags` and ignores `completedWithoutTags`; the terminal `COMPLETE` is published with `terminating = true` | [#257](https://github.com/metravolokos/enoTone-android/issues/257) |
| Invalidation removes the permanent ref, but the next request migrates the legacy copy; `remoteModified` is not part of the key | [#256](https://github.com/metravolokos/enoTone-android/issues/256) |
| `artworkJob` can be cancelled by a repeated `refreshArtwork()` while `selecting` holds `LOADING`; a transition to `PLAYING` without a `trackId` change does not restart artwork loading | Measurement and a test before fixing |
| After an album refresh both `reload()` and `LibraryDataRevision.bump()` run — two reloads | [#258](https://github.com/metravolokos/enoTone-android/issues/258) |
| Installing the APK outside an app store triggers a generic "threat detected" warning from the installer on some vendor ROMs (first seen on `0.6.0-preview.429`). This is a general warning for apps outside the store; permissions and libraries have been checked, there are no third-party SDKs | Tap "Install"; the only way to remove the warning is publishing to AppGallery / F-Droid — see OPEN_QUESTIONS |
| Compose BOM `2026.11.01` (Compose 1.13) requires compileSdk `37.1`; waiting for publication and an owner decision | Stay on the published stable BOM `2026.09.00`; do not change compileSdk without an owner decision |
| Some older `tools/test_*_contract.py` still check source text instead of behaviour; 6 contract files were already replaced, 16 remain | Replace the rest with behavioural JUnit/Robolectric tests; do not add new text contracts |
| 429 pre-existing detekt findings in the baseline | Must decrease; growth is blocked by CI ([ADR-0076](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0076-quality-tooling.md)) |
| There is no separate confirmation that KSP 2.3.13, Room 2.8.5 and Roborazzi 1.79.0 officially support Gradle 9.9.1 + AGP 9.5.1 | Empirically the Android M0 CI is green; this is not a vendor guarantee ([ADR-0100](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0100-gradle9-agp9-compilesdk37.md)) |
| detekt 1.23.8 does not officially support Gradle 9 and AGP 9; detekt 2.x exists only as alphas | Migration to 2.x only by a separate owner decision once a stable release exists |
| R8 in AGP 9 prunes resources more aggressively: `isShrinkResources = true` removed 156 resources, including notification layouts from `androidx.core` and Media3 | Both indexing services use the platform `Notification.Builder`; `EnoTonePlaybackService` extends `MediaSessionService` and keeps the standard Media3 provider. Verify against the release build artefacts in CI (`resources.txt`, `mapping.txt`), not on a device ([ADR-0100](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0100-gradle9-agp9-compilesdk37.md)) |
| No release workflow triggered by a tag | PR #198 (R-A1) added a minified APK build but not signing or tag publishing |
| No `LICENSE` file (owner decision) | M6-0/8 |

## Requires an owner decision

| Problem | What to decide |
|---|---|
| Legacy sign-out journal with `pending=true`, empty vault and non-empty Room without a manifest | Fail-closed per [ADR-0103](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0103-legacy-signout-recovery.md): such a leftover is not recovered automatically. Either build a manual completion screen (option 2 in the ADR) or accept it as is |
| Ten scenarios without automated coverage | Either add tests or explicitly accept the gap — see `docs/SCENARIOS.md` (Russian) |
| Sorting, playlists, offline cache, global search, diagnostics export | See [#266](https://github.com/metravolokos/enoTone-android/issues/266) |

## Not implemented from the original spec (scope, not defects)

Diagnostic report export and a ring log buffer; `ACTION_VIEW`/`SEND`; SAF source; AI settings and interfaces; playlist UI; landscape and tablets (orientation is locked to portrait).

Queue deletion and reordering (Q2, #193) were cancelled by the owner; returning to them requires the owner's initiative and a design. Tag editing with cloud write-back (TAG-EDIT, #200) was **cancelled by the owner on 2026-11-21** — not deferred: the feature is too complex for a simple player. Tag reading, indexing and tag-based grouping remain in place.

## Limitations accepted by the owner

Flat `/AllMusic` without recursion; no Wi-Fi-only mode; artwork is permanent storage without automatic eviction. D3.4 / R-3 and R-5 were replaced by the D3.5 decision (#168, ADR-0095): predictive back-plate and shared-artwork flight are no longer required. These are not defects.

## See also

- [QUEUE](QUEUE.md) — when these will be addressed
- Russian canonical: [`docs/KNOWN_ISSUES.md`](../KNOWN_ISSUES.md)
