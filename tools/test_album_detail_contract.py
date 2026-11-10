"""M2 full album page: local header, artwork, and existing Media3 queue."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/app/enotone/android"


class AlbumDetailContractTest(unittest.TestCase):
    def src(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_album_header_is_lazy_list_item_and_other_tabs_unchanged(self):
        screen = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        for required in (
            "section == LibraryPage.ALBUMS && selectedGroup != null",
            'item(key = "album-detail-header")',
            "AlbumDetailHeader(",
            "target = albumArtworkTargets[selectedGroup.key]",
            "items(visible, key = { it.id })",
            "LazyVerticalGrid(",
            "GridCells.Adaptive(minSize = 148.dp)",
            "selectedGroupKey = group.key",
            "LibraryPage.ARTISTS -> OfflineLibraryGrouping.artists(tracks)",
            "LibraryPage.ALBUM_ARTISTS -> OfflineLibraryGrouping.albumArtists(tracks)",
        ):
            self.assertIn(required, screen)
        self.assertLess(screen.index('item(key = "album-detail-header")'),
                        screen.index("items(visible, key = { it.id })"))
        self.assertNotIn("deleteDatabase(", screen)

    def test_album_play_all_uses_full_group_and_existing_playback_path(self):
        screen = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        for required in (
            "val albumTracks = remember(tracks, section, selectedGroup)",
            "tracks.filter { it.id in selectedGroup.trackIds }",
            "val playTrack: (OfflineTrackRow, List<OfflineTrackRow>) -> Unit",
            "playTrack(first, albumTracks)",
            "playTrack(track, visible)",
            "val queue = contextTracks.mapNotNull { shown ->",
            "preparedById[shown.id]",
            "service.playQueue(accountEmail, appPassword,",
            "currentTrackId = track.id",
            "canPlay = albumTracks.isNotEmpty()",
        ):
            self.assertIn(required, screen)
        # One Media3 start path, shared by individual tracks and Play album.
        self.assertEqual(1, screen.count("service.playQueue(accountEmail, appPassword,"))
        # Opening the album and starting playback must never query Room.
        # The new user-confirmed metadata refresh has its own explicit IO
        # coroutine and is the only allowed database access in this region.
        playback = screen[screen.index("val playTrack: (OfflineTrackRow,"):
                          screen.index("// Explicit confirmation before ANY album tag HTTP.")]
        self.assertNotIn("openLibraryDatabase(", playback)
        self.assertIn("openLibraryDatabase(context)", screen[
            screen.index("// Explicit confirmation before ANY album tag HTTP."):
            screen.index("if (showPlayerDetails && selectedTitle != null)")])

    def test_album_header_reuses_cached_artwork_and_never_invents_year(self):
        header = self.src("app/src/main/java/app/enotone/android/AlbumDetailHeader.kt")
        for required in (
            "LaunchedEffect(group.key, target, accountEmail, appPassword)",
            "repository.cover(accountEmail, appPassword, target)",
            "group.subtitle?.takeIf",
            "group.trackIds.size",
            "R.drawable.ic_nav_albums",
            "R.string.album_cover_description",
            "R.string.album_play_all",
            "Button(",
            "enabled = canPlay",
            "onClick = onPlayAll",
            "modifier = Modifier.size(200.dp)",
        ):
            self.assertIn(required, header)
        self.assertNotIn("WebDavRangeReader(", header)
        self.assertNotIn("openLibraryDatabase(", header)
        self.assertNotIn("year = ", header)
        # Release year and disc/track numbers are nullable, never fabricated.
        entity = self.src("core/database/src/main/java/app/enotone/database/RemoteTrackEntity.kt")
        self.assertIn("val year: Int? = null", entity)
        self.assertIn("val trackNumber: Int? = null", entity)
        self.assertIn("val discNumber: Int? = null", entity)

    def test_localization_and_stable_data(self):
        for path in ("app/src/main/res/values/strings.xml",
                     "app/src/main/res/values-en/strings.xml"):
            root = ET.fromstring(self.src(path))
            keys = {item.attrib["name"] for item in root.findall("string")}
            self.assertIn("album_play_all", keys)
            self.assertIn("album_cover_description", keys)
        database = self.src(
            "core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        self.assertIn("version = 3", database)


if __name__ == "__main__":
    unittest.main()
