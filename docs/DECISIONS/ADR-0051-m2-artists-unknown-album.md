# ADR-0051 — Separate Track Artists and Album Artists; Unknown Album

Date: 2026-11-10. Status: Draft until exact-HEAD CI + Huawei verification.

## User decision supersedes the previous grouping rule

After scanning all 3,590 FLAC/MP3 files, local Room inspection showed 3,589 records with ALBUM and 3,447 with ALBUM ARTIST. The user confirmed the missing metadata is genuine. Therefore **neither field can be required for a track to remain visible in the appropriate library view**. The earlier M2 rule ("Artists prefers Album Artist, falls back to track Artist") must be replaced for artist navigation, while Album Artist remains primary for **album identity**, not a replacement for the track's Artist.

## Effective navigation

- **Tracks** — every present file in the local catalogue, even with missing tags.
- **Albums** — real album titles grouped by normalized (ALBUM, ALBUM ARTIST if known, else ARTIST). Real saved metadata with a missing/blank ALBUM belongs to a single virtual group **Unknown Album / Неизвестный альбом**. All such tracks share that group even when their performers differ. The virtual key `album:missing` is not equal to a real embedded album named "Unknown Album".
- **Artists / Исполнители** — group by the individual track's ARTIST only. Never substitute ALBUM ARTIST on this page.
- **Album Artists / Исполнители альбомов** — group by the separate ALBUM ARTIST field only. Do not infer or invent a missing ALBUM ARTIST from ARTIST. The 143 known files lacking the former remain on Tracks, Albums and Artists; they do not produce fictitious album-artist groups.
- Unindexed files with no stored tag fields remain under Tracks until scanning, **not** in a fabricated giant Unknown Album. This avoids restoring a pre-scanning problem now that the collection is indexed.
- Album and performer groups are a pure, local projection of existing Room records; no reimport, HTTP GET, new SQLite columns, folder-based inference or playback engine changes.

## Future Settings requirement

Use stable IDs `TRACKS`, `ALBUMS`, `ARTISTS`, `ALBUM_ARTISTS` and an independent `LibraryPageVisibility` policy. Today all four pages are visible. A later Settings task will persist switches to hide any page, without deleting tracks or metadata, and will fall back to Tracks if all are hidden. **No settings switches are implemented in this PR.** Leave present UI colors at standard Material defaults until design is approved.

## Verification

JUnit: independent ARTIST and ALBUM ARTIST membership, same album name with different album artists kept separate, one Unknown Album across different track performers, localizable label, literal embedded "Unknown Album" does not collide, title-only scanned metadata counts, untagged filename fallback does not create unknown groups, stable visible-page IDs/fallback. CI: Android build, JUnit, Lint, Room schema, GMS-free and APK gate. Hardware: signed Stable Preview over #223; all 3590 tracks remain, missing ALBUM appears as one Unknown Album entry, 143 missing ALBUM ARTIST do not disappear from other views, both performer pages navigate and play correctly.
