# ADR-0052 — Persistent visibility settings for library pages

Date: 2026-11-10. Status: Accepted after successful CI #226/#227 and Huawei owner smoke.

## Context
The owner approved Stable Preview #225 on Huawei: Tracks, Albums (with Unknown Album), Artists (track-level ARTIST) and Album Artists (ALBUM ARTIST) are distinct and working. They previously requested the ability to hide selected library pages from Settings.

## Decision
- Show **Library page settings** from the offline library; it opens a small Material 3 settings dialog with four independent checkboxes. All pages are shown initially.
- The user may hide any page while **at least one remains visible**. The last visible page's checkbox is disabled to prevent an inaccessible library. All four page IDs remain stable; page labels are localized RU/EN.
- Store only a set of non-sensitive stable page identifiers in private on-device SharedPreferences under hidden_pages_v1. No account ID, password, music or WebDAV path is stored. Unknown persisted IDs from other versions are ignored.
- If a selected page becomes hidden, navigate to the first visible page, clear the stale selected group and reset the search text. Playback engine state remains untouched.
- Hiding a page changes only presentation. The full 3,590-record Room catalogue, tag values, search indexes, WebDAV sync, Media3 and the signing certificate remain unchanged. Re-enabling a page reveals its original groups without scanning.
- Use standard Material controls and colors; the final dusty-rose/graphite/cream design is not approved for this production UI yet.

## Acceptance
Exact HEAD CI covers pure JVM page toggle/persistence name round-trip, unknown values, last-visible guard, UI wiring, localized strings, Android Build/JUnit/Lint, Room v2 identity, GMS, stable Preview build. Signed Stable Preview from main should install **over #225** with original Room still populated.

On Huawei: hide Artists, verify Album Artists still appears; hide Album Artists independently; close and reopen app to confirm persistence; re-enable both; try to hide every page and verify last cannot be disabled. Check an Unknown Album song still plays and all tracks remain. Do not reindex or re-import.

## Hardware acceptance — 2026-11-10
CI #226 completed successfully. PR #50 merged as `1187feea99f39b8c723b24bbd3adee0c587c51d5`. Main Stable Preview #227 was signed and published with successful CI. After testing on Huawei the owner reported **«Все работает»**. The feature is therefore accepted; no further implementation changes requested. This is broad user acceptance, not independent evidence for every individual smoke-case from the checklist.
