# Room schema history

`app.enotone.database.EnoToneDatabase/1.json` is the **actual Room/KSP schema v1** exported in successful GitHub Actions run [#24](https://github.com/metravolokos/enoTone-android/actions/runs/42470949763) from commit `5e36ea409b562f6173fd516cf764130b7bee479c`.

Review and commit exported versions before changing database structure; migrations must be explicit, never use destructive fallback. Schema v5 adds nullable track duration plus playlist and playlist-item tables. Its migration tests cover v4 data preservation; this is **not** a claim that an Android device migration was tested.

Schema v6 adds the disposable FTS4 `track_search` index (ADR-0087). The committed `6.json` is the exact Room/KSP export from CI #587; existing data and playlists are preserved by migration 5→6. Migration tests and Huawei install-over-install acceptance remain distinct checks.
