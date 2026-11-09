# ADR-0050 — M1: on-device tag audit, explicit reindex and incremental /AllMusic refresh

Date: 2026-11-10. Status: Draft until exact-HEAD CI and Huawei verification.

## Hardware evidence

Stable Preview #211 completed one user-started continuous scan: 42 batches,
2144 file attempts, 2143 successful tag reads, 1 transient network error,
2490 GET / 163119104 bytes, 0 remaining eligible files, stop NO_ELIGIBLE_FILES.
The local offline Room audit then reported **3590 present files, 3590 records
with some tags, 3590 titles, 3590 artists, 3589 albums, 3447 album artists**.
The missing album and 143 absent Album Artist values are **not proof of
parser faults**. The tags may truly be absent from the original MP3/FLAC files.

The user wants to identify the track with no album, inspect its stored metadata,
reindex one or all tracks, and discover new/changed files without losing a
completed library.

## Decision

1. Local, opt-in audit in the diagnostic screen: list up to 30 files with
   NULL album or NULL Album Artist. A local substring search by filename or
   title can find any track. Only the on-device screen may display filenames,
   titles or tag values; no remote logging, crash telemetry or GitHub reports.
   A NULL Album Artist still permits the accepted Artist fallback.
2. Explicit queue flag **RETRY_REFRESH** stored in existing nullable
   `tagScanCode`, **no Room schema change**. A selected row is marked for
   re-reading. A separately confirmed bulk action marks all present MP3/FLAC
   rows. These UPDATE statements preserve title, artist, album, albumArtist.
   No network access is triggered by queuing; the owner presses the existing
   managed Start/Index all button.
3. Existing `ParallelTagIndexer` and `PilotTagIndexer` select queued
   `RETRY_*` rows even when last-known-good tags already exist. The
   per-file Room compare-and-set checks id/account/path/ETag/size/mtime.
   On successful ID3/FLAC parse, replace tag values including deliberately
   absent fields. On a timeout, HTTP failure, unsupported layout or no embedded
   metadata, **retain prior tag values**; keep the outcome code so it can be
   audited. No blind delete/reset/re-import of all 3590 tags.
4. Rename existing shallow `FlatLibraryWebDavClient().list()` action to
   **Refresh /AllMusic catalogue**. This is one explicit Depth:1 PROPFIND,
   no recursive scan. `LibrarySnapshotPlanner` retains metadata and stable
   ids for unchanged files, creates new local entries with no tags (eligible
   for the next index session), and invalidates tag checkpoints for actual
   remote ETag/size/mtime changes. Incomplete listings never mark files
   missing; no DELETE. Do not run this operation concurrently with the
   long-running foreground indexer.
5. Four parallel bounded Range readers, one Room collector, retry/backoff,
   optional background processing, no Wi-Fi gating, Keystore and Media3 are
   **unchanged**. New fields/filters are diagnostic only. No other formats
   introduced: user currently owns FLAC and a few MP3.

## Acceptance criteria

- Exact-HEAD CI: Room/KSP, Android JUnit/Lint, parser/retry contract, snapshot
  planner test verifies one new track added while unchanged metadata survives,
  queued and previously tagged candidates are reselected without reprocessing
  all other complete rows.
- Stable Preview same packageId/signing key, install **over #211** without
  uninstall/clearing the local library or credentials.
- Huawei: inspect the single missing-album file offline; confirm its filename
  and existing title/artist/albumArtist. Queue only that row, start managed
  indexing, re-run offline coverage and inspect again. A still-NULL album
  after a successful re-read can be genuine source metadata absence.
- Add one file to /AllMusic, press Refresh catalogue once; report +1 inserted,
  3590 unchanged, no loss of existing metadata. Run managed index again to
  read only that new file. Do not ask user to add a test file unless they
  choose to; synthetic JVM tests cover the merge without touching real data.
- Optional: confirm full reindex; before any GET old title/artist/album tags
  remain visible, and the scan can be paused/resumed with existing quotas.
  No automatic full reindex without explicit confirmation.

## Safety/limits

Bulk reindex genuinely performs thousands of HTTP GETs when started; the
confirmation is deliberate. A failed re-read cannot wipe old valid tags.
Updated remote files can legitimately have different tags: changed ETags
invalidate snapshots as before, and are indexed again. Unsupported extensions
are not marked for refresh. Keep personal filenames and metadata off public
CI outputs and issue comments.
