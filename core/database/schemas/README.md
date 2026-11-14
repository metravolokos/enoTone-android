# Room schema history

`app.enotone.database.EnoToneDatabase/1.json` is the **actual Room/KSP schema v1** exported in successful GitHub Actions run [#24](https://github.com/metravolokos/enoTone-android/actions/runs/42470949763) from commit `5e36ea409b562f6173fd516cf764130b7bee479c`.

Review and commit exported versions before changing database structure; migrations must be explicit, never use destructive fallback. Schema v5 adds nullable track duration plus playlist and playlist-item tables. Its migration tests cover v4 data preservation; this is **not** a claim that an Android device migration was tested.
