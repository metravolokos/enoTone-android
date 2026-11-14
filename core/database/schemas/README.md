# Room schema history

`app.enotone.database.EnoToneDatabase/1.json` is the **actual Room/KSP schema v1** exported in successful GitHub Actions run [#24](https://github.com/metravolokos/enoTone-android/actions/runs/42470949763) from commit `5e36ea409b562f6173fd516cf764130b7bee479c`.

Review and commit exported versions before changing database structure; migrations must be explicit, never use destructive fallback. Schema v5 adds nullable track duration plus playlist and playlist-item tables. Its migration tests cover v4 data preservation; this is **not** a claim that an Android device migration was tested.

Schema v6 added the disposable FTS4 `track_search` index (historical ADR-0087). The committed `6.json` is the exact Room/KSP export from CI #587.

Schema v7 removes FTS4 (ADR-0091, Draft PR #143). The manually registered migration 6→7 drops only `track_search`; an OEM SQLiteException during DROP is logged by a fixed code and does not abort migration. `remote_tracks`, `playlists`, `playlist_items`, artwork files and queue checkpoints are not erased. The remaining Room entities are identical to v5, so the exported schema has the same identity hash as v5 (`3e081437b5dda287e7f5b1871a8f6b8d`). This is a committed expected schema; exact KSP/CI verification and Huawei install-over-install acceptance remain pending.
