"""Optional MP3/FLAC disc/track/year tags: preserve private Room v2 data on v3."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ExtendedTagMetadataContractTest(unittest.TestCase):
    def src(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_nondestructive_room_v3_and_existing_base_migrations(self):
        db = self.src("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        entity = self.src("core/database/src/main/java/app/enotone/database/RemoteTrackEntity.kt")
        main = self.src("app/src/main/java/app/enotone/android/MainActivity.kt")
        for token in ('version = 4', 'AutoMigration(from = 1, to = 2)',
                      'AutoMigration(from = 2, to = 3)',
                      'AutoMigration(from = 3, to = 4)', 'exportSchema = true'):
            self.assertIn(token, db)
        for field in ('year', 'trackNumber', 'discNumber'):
            self.assertIn(f'val {field}: Int? = null', entity)
        # M6-0/2: the builder moved into the shared EnoToneDatabase.getInstance().
        self.assertIn('"enotone-local-library.db"', db)
        self.assertNotIn('fallbackToDestructiveMigration', db + main)

    def test_fast_parser_and_bounded_cas_persist_new_metadata(self):
        parser = self.src("data/library/src/main/java/app/enotone/library/FastAudioTagReader.kt")
        fields = self.src("data/library/src/main/java/app/enotone/library/AudioTagFields.kt")
        dao = self.src("core/database/src/main/java/app/enotone/database/RemoteTrackDao.kt")
        parallel = self.src("data/library/src/main/java/app/enotone/library/ParallelTagIndexer.kt")
        single = self.src("data/library/src/main/java/app/enotone/library/LibraryTagStore.kt")
        planner = self.src("data/library/src/main/java/app/enotone/library/LibrarySnapshotPlanner.kt")
        for token in ('"TDRC"', '"TYER"', '"TRCK"', '"TPOS"',
                      '"DATE"', '"YEAR"', '"TRACKNUMBER"', '"DISCNUMBER"'):
            self.assertIn(token, parser)
        for token in ('parseYear(rawYear)', 'parseOrdinal(rawTrackNumber, 999)',
                      'parseOrdinal(rawDiscNumber, 99)'):
            self.assertIn(token, fields)
        for token in ('year = :year', 'trackNumber = :trackNumber',
                      'discNumber = :discNumber', 'ELSE year END',
                      'ELSE trackNumber END', 'ELSE discNumber END',
                      'remoteEtag IS :etag', 'remoteModified IS :modified'):
            self.assertIn(token, dao)
        for token in ('year = attempt.tags?.year',
                      'trackNumber = attempt.tags?.trackNumber',
                      'discNumber = attempt.tags?.discNumber'):
            self.assertIn(token, parallel)
        for token in ('year = tags.year', 'trackNumber = tags.trackNumber',
                      'discNumber = tags.discNumber'):
            self.assertIn(token, single)
        for field in ('year', 'trackNumber', 'discNumber'):
            self.assertIn(f'{field} = if (sameVersion) old?.{field} else null', planner)

    def test_album_display_never_forces_mass_rescan_or_fakes_values(self):
        screen = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        catalog = self.src("data/library/src/main/java/app/enotone/library/OfflineTrackCatalog.kt")
        header = self.src("app/src/main/java/app/enotone/android/AlbumDetailHeader.kt")
        for token in ('OfflineTrackCatalog.albumOrder(', 'OfflineTrackCatalog.commonAlbumYear(',
                      'year = albumYear', 'track.trackNumber?.toString().orEmpty()'):
            self.assertIn(token, screen)
        self.assertIn('val year: Int? = null', catalog)
        self.assertIn('fun albumOrder(', catalog)
        self.assertIn('fun commonAlbumYear(', catalog)
        self.assertIn('year?.toString()', header)
        self.assertIn('genre?.takeIf { it.isNotBlank() }', header)
        self.assertIn('Text(facts,', header)
        self.assertNotIn('WebDavRangeReader(', screen)
        self.assertNotIn('queueAllTagRefresh(', screen)
        self.assertNotIn('fallbackToDestructiveMigration', screen)


if __name__ == "__main__":
    unittest.main()
