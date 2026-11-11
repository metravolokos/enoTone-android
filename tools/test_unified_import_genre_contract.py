"""Unified import: one explicit listing + bounded background tags, including genre."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class UnifiedImportGenreContractTest(unittest.TestCase):
    def src(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_one_click_import_commits_room_before_resumable_scan(self):
        ui = self.src("app/src/main/java/app/enotone/android/MainActivity.kt")
        start = ui.index("// One explicit action: quick listing + background metadata completion.")
        end = ui.index("// Explicit full reindex is a DIFFERENT action", start)
        section = ui[start:end]
        for token in (
            "FlatLibraryWebDavClient().use { client ->",
            "val listing = client.list(email, password)",
            "val plan = store.save(email, listing)",
            "val pending = database.remoteTrackDao().pendingFastScans(",
            "startMetadata = result[5] > 0",
            "busy = false",
            "if (startMetadata) startManagedSession(true)",
        ):
            self.assertIn(token, section)
        self.assertLess(section.index("val plan = store.save(email, listing)"),
                        section.index("if (startMetadata) startManagedSession(true)"))
        self.assertLess(section.index("busy = false"),
                        section.index("if (startMetadata) startManagedSession(true)"))
        self.assertEqual(1, section.count("startManagedSession(true)"))
        self.assertNotIn("queueAllTagRefresh(", section)
        self.assertNotIn("deleteDatabase(", section)
        self.assertIn("onClick = { startManagedSession(true) }", ui)  # advanced tools

    def test_metadata_uses_existing_fast_pass_with_genre_not_more_requests(self):
        parser = self.src(
            "data/library/src/main/java/app/enotone/library/FastAudioTagReader.kt")
        fields = self.src("data/library/src/main/java/app/enotone/library/AudioTagFields.kt")
        for token in (
            '"TCON"', 'tags["TCON"]',
            '"GENRE"', 'values["GENRE"]',
            "Id3Genres.fromNumber(u(end[127]))",
            "rawGenre = rawGenre",
        ):
            self.assertIn(token, parser)
        self.assertIn("genre = Id3Genres.normalize(rawGenre)", fields)
        self.assertIn("val genre: String? = null", fields)
        self.assertNotIn("readGenreOnly(", parser)

    def test_genre_nondestructive_room_cas_and_snapshot(self):
        db = self.src("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        dao = self.src("core/database/src/main/java/app/enotone/database/RemoteTrackDao.kt")
        entity = self.src("core/database/src/main/java/app/enotone/database/RemoteTrackEntity.kt")
        planner = self.src("data/library/src/main/java/app/enotone/library/LibrarySnapshotPlanner.kt")
        parallel = self.src("data/library/src/main/java/app/enotone/library/ParallelTagIndexer.kt")
        pilot = self.src("data/library/src/main/java/app/enotone/library/PilotTagIndexer.kt")
        single = self.src("data/library/src/main/java/app/enotone/library/LibraryTagStore.kt")
        for token in ("version = 4", "AutoMigration(from = 1, to = 2)",
                      "AutoMigration(from = 2, to = 3)",
                      "AutoMigration(from = 3, to = 4)", "exportSchema = true"):
            self.assertIn(token, db)
        self.assertIn("val genre: String? = null", entity)
        self.assertIn("genre = :genre", dao)
        self.assertIn("genre = CASE WHEN :code", dao)
        self.assertIn("ELSE genre END", dao)
        self.assertIn("genre = if (sameVersion) old?.genre else null", planner)
        self.assertIn("genre = attempt.tags?.genre", parallel)
        self.assertIn("genre = tags?.genre", pilot)
        self.assertIn("genre = tags.genre", single)
        self.assertNotIn("fallbackToDestructiveMigration", db)

    def test_genre_search_and_album_header_are_local(self):
        catalog = self.src("data/library/src/main/java/app/enotone/library/OfflineTrackCatalog.kt")
        ui = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        header = self.src("app/src/main/java/app/enotone/android/AlbumDetailHeader.kt")
        self.assertIn("genre = track.genre?.takeIf", catalog)
        self.assertIn("row.genre", catalog)
        self.assertIn("fun commonAlbumGenre(", catalog)
        self.assertIn("OfflineTrackCatalog.commonAlbumGenre(albumTracks)", ui)
        self.assertIn("genre = albumGenre", ui)
        self.assertIn("genre?.takeIf { it.isNotBlank() }", header)
        self.assertIn("Text(facts,", header)
        self.assertNotIn("WebDavRangeReader(", header)

    def test_cover_policy_remains_lazy_not_a_bulk_import(self):
        art = self.src("app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt")
        ui = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        self.assertIn("Semaphore(2)", art)
        self.assertIn("MAX_SIDE = 1200", art)
        self.assertIn("context.applicationContext.filesDir", art)
        self.assertIn("AlbumArtworkTile(", ui)
        self.assertIn("AlbumDetailHeader(", ui)


if __name__ == "__main__":
    unittest.main()
