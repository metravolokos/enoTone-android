# KNOWN ISSUES — current defects and limitations

> **Translated from:** `docs/KNOWN_ISSUES.md` at `main` `92348b9f`, 2026-11-23. Russian is canonical.

**Snapshot:** 2026-11-23 · version `0.6.2` · `main` `92348b9f`. Acceptance is by automated checks ([ADR-0104](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0104-auto-acceptance.md)). A defect found on a device becomes a separate Issue labelled `bug`.

Only defects confirmed on the current code belong here. Findings that need an owner decision or a new ADR are tracked in Issues, not in this list.

## Playback

| Problem | Consequence | Plan |
|---|---|---|
| The first queue after a service start may play without the transient cache: it is prepared in the background and picked up for the next queue (ADR-0094) | The first track after a service restart is fetched over the network | Revisit if it becomes noticeable: warm the cache on app start rather than in `playQueue` |
| File size and ETag are captured when the queue is created; response `416` is not handled | A file changed on the server will not play until the queue is recreated | Requires an ADR on the network layer |
| The playback service keeps its binding while paused in the background: `isForeground=true`, `am stopservice` is rejected | The states that `#258` (silent `bindService`) and the #268 regression were written for are nearly unreachable in practice | See [#273](https://github.com/metravolokos/enoTone-android/issues/273) — awaiting an owner decision |
| `OPEN_NOW_PLAYING` extra does not open Now Playing on an already running activity without `NEW_TASK` / single-top | External calls break; the notification itself sends correct flags, so the user path is unaffected | See [#274](https://github.com/metravolokos/enoTone-android/issues/274) |
| `restoredAccount` is not released after a failed restore | A single transient read failure can block queue restore; the fix ships with a bounded retry budget | [#254](https://github.com/metravolokos/enoTone-android/issues/254) |
| Prefetch cancellation race in `TransientMediaCache.prefetchNext` | A download can start after the track changed or playback stopped | [#255](https://github.com/metravolokos/enoTone-android/issues/255) |
| Bluetooth MediaSession does not implement `onPlaybackResumption` after process death | Headset auto-resume does not work without opening the app | Separate PR |
| `bindService` returned `true` but the service never connected: an error is shown, but no automatic re-bind | Dead end without restarting the app | [#258](https://github.com/metravolokos/enoTone-android/issues/258) |

## Data and privacy

| Problem | Consequence | Plan |
|---|---|---|
| Sign-out does not guarantee that audio data is removed before the Keystore is cleared: `stopPlayback()` only enqueues cleanup, suppresses errors and does not close the open `SimpleCache` | The Keystore is cleared while files may still be on disk — the fail-closed order of ADR-0080 is violated | [#256](https://github.com/metravolokos/enoTone-android/issues/256), risk class R |
| Scoped sign-out with another account present cleans only the permanent artwork store; legacy `cacheDir/enotone-artwork-v2` survives and is restored after signing in again | An old cover image returns after sign-out → sign-in | [#256](https://github.com/metravolokos/enoTone-android/issues/256), risk class R |

## Data and network

| Problem | Consequence | Plan |
|---|---|---|
| Track ID is `SHA-256(source + encodedPath)` without encoding canonicalisation; a rename becomes a new track | Renaming loses the playlist entry | Limitation of ADR-0085 |
| Tags are read for MP3/FLAC only; ID3v2.3 tag-level unsync is deliberately rejected; MPEG-2/2.5 and hybrid unsync lack fixtures | Other formats and rare ID3 cases are partially supported | Separate decision and tests ([#182](https://github.com/metravolokos/enoTone-android/issues/182)) |
| Three HTTP stacks (Ktor CIO, `HttpURLConnection`, `HttpsURLConnection`) with no shared backoff with jitter | Duplicated logic; system proxy/VPN behaviour for Ktor CIO is unverified | Requires an ADR on a unified HTTP stack |

## Architecture and quality

| Problem | Plan |
|---|---|
| Installing the APK outside an app store triggers a generic "threat detected" warning from the installer on some vendor ROMs. This is a general warning for apps outside the store; permissions and libraries have been checked, there are no third-party SDKs | Tap "Install"; the only way to remove the warning is publishing to a store — see OPEN_QUESTIONS |
| Compose BOM `2026.11.01` (Compose 1.13) requires compileSdk `37.1`; waiting for publication and an owner decision | Stay on the published stable BOM `2026.09.00`; do not change compileSdk without an owner decision |
| Some older `tools/test_*_contract.py` still check source text instead of behaviour; 6 contract files were already replaced, 16 remain | Replace the rest with behavioural JUnit/Robolectric tests; do not add new text contracts |
| 429 pre-existing detekt findings in the baseline | Must decrease; growth is blocked by CI ([ADR-0076](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0076-quality-tooling.md)) |
| No release workflow triggered by a tag | PR #198 added a minified APK build but not signing or tag publishing |
| No `LICENSE` file (owner decision) | Owner has not chosen a license yet |

## Requires an owner decision

| Problem | What to decide |
|---|---|
| Legacy sign-out journal with `pending=true`, empty vault and non-empty Room without a manifest | Fail-closed per [ADR-0103](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0103-legacy-signout-recovery.md): such a leftover is not recovered automatically. Either build a manual completion screen (option 2 in the ADR) or accept it as is |
| Six scenarios without automated coverage | Either add tests or explicitly accept the gap — see `docs/SCENARIOS.md` (Russian) |
| Sorting, playlists, offline cache, global search, diagnostics export | See [#266](https://github.com/metravolokos/enoTone-android/issues/266) |

## Not implemented from the original spec (scope, not defects)

Diagnostic report export and a ring log buffer; `ACTION_VIEW/SEND`; SAF source; AI settings and interfaces; playlist UI; landscape and tablets (orientation is locked to portrait).

Queue deletion and reordering (Q2, #193) were cancelled by the owner; returning to them requires the owner's initiative and a design. Tag editing with cloud write-back (TAG-EDIT, #200) was **cancelled by the owner on 2026-11-21** — not deferred: the feature is too complex for a simple player. Tag reading, indexing and tag-based grouping remain in place.

## Limitations accepted by the owner

Flat `/AllMusic` without recursion; no Wi-Fi-only mode; artwork is permanent storage without automatic eviction. D3.4 / R-3 and R-5 were replaced by the D3.5 decision (#168, ADR-0095): predictive back-plate and shared-artwork flight are no longer required. These are not defects.

## See also

- [QUEUE](QUEUE.md) — when these will be addressed
- Russian canonical: [`docs/KNOWN_ISSUES.md`](../KNOWN_ISSUES.md)
