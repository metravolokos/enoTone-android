# ADR-0049 — M1: continuous user-initiated background tag indexing

Date: 2026-11-09. Status: Draft pending exact-HEAD CI and Huawei smoke.

## Baseline and why
Stable Preview #205 was tested on Huawei: **4/4 batches, 207/207 successful tags, 0 transient/permanent errors, 240 GET, 15 MiB downloaded, 3274 eligible files remaining, stop SESSION_LIMIT**. The serial Room collector with four concurrent HTTP readers removed the previously observed `INDEX_BATCH / STATE` symptom for this bounded session. This is evidence for the four-worker network load, **not** yet for continuous operation over hours or other formats.

The owner requested an explicit **single-start pass over the remaining library** rather than ~16 repeated manual four-batch sessions. Wi-Fi filtering is not required. Indexing **must work with the UI backgrounded and device locked**. No recursive PROPFIND or destructive reimport.

## Implementation
- Keep **two explicitly user-selected modes**: BOUNDED (unchanged 4-batch hardware-tested button) and CONTINUOUS ("Индексировать все оставшиеся MP3/FLAC").
- The current **dataSync foreground service** runs either mode while the phone is locked, with existing notification Pause/Stop. The selected mode and credentials are handed off **in RAM** only; the Intent contains only the action string. Never transmit credentials in notification, URL, logs, SQLite or Intent.
- Four independent WebDAV Range readers, each with existing per-file 64-KiB blocks and at most six GET per attempt. **One serial Room collector** and shared atomic budget. Per batch: ≤60 GET and ≤4 MiB, ~180-sec soft time limit, 1.5-sec gap. Never remove these caps to speed up full indexing.
- CONTINUOUS chains up to **80 bounded batches** (≤4,800 GET, ≤320 MiB total reserved budget) with a **four-hour monotonic wall cap**. Expected coverage ~3,274 MP3/FLAC pending based on the observed 207 / 240 GET. This is a safety ceiling, not a promise of a four-hour run or exact completion time.
- The existing up-to-two per-file retries continue. For a server-wide `RETRY_BACKOFF` in CONTINUOUS, allow at most two automatic cooldown retries (**30 seconds then 60 seconds**); terminate on the third failed wave. 401/403 stops immediately. Stop early on three consecutive batches consuming requests without finishing a file. If no eligible files remain, finish automatically.
- Any interruption preserves completed rows in Room. Resume is a new explicit button press and selects only pending records; no hidden self-restart on boot/process kill, no background WorkManager. Android 15+ dataSync foreground-service 6h/24h quota applies across multiple starts, so the 4h per-run limit is **not** a guarantee of unlimited background runtime. OS timeout cancels the service safely and later resume is explicit.
- MP3/FLAC are the supported fast-metadata formats. The complete library as defined in the original spec also includes OGG/Opus/M4A/AAC/WAV; **these need independent parser work**. A "no eligible files" result does not imply unsupported formats were indexed.

## Gates
1. Exact-HEAD Android Gradle build, JUnit, Lint, Mock WebDAV, Room schema identity, no-GMS, and debug APK check. Tests cover legacy four-batch cap, 80-batch continuous cap, no-pending early exit, time budget, escalating cooldown, exhausted failure cap, auth failure, no auto resume, no credential leakage in Intent.
2. Merge only after green CI; GitHub main signing workflow must publish enotone-stable-preview-apk with the stable package ID/certificate. Update over #205, never uninstall or clear the library (3590 entries and cached tags).
3. Huawei smoke: confirm 3274 pending baseline; run continuous mode explicitly with app backgrounded and screen locked. Initially observe >4 batches / >240 GET *across the same user-started run* while errors remain zero. Test notification Pause and resumption; allow full pass only if server behavior remains stable.
4. Report anonymized counters, total elapsed, and terminal stop code. Do not expose track names, credentials or remote paths.

## Out of scope
No full-collection new PROPFIND, no change in playback/Media3, no style changes, no automatic scheduled check without consent, no parallel Room writers, no forced switch to Wi-Fi.
