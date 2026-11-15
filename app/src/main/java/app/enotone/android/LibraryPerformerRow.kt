package app.enotone.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.enotone.android.core.ui.component.EnoIcons
import app.enotone.android.core.ui.component.EnoLetterAvatar
import app.enotone.android.core.ui.theme.EnoColors
import app.enotone.library.LibraryPage
import app.enotone.library.OfflineLibraryGroup
import app.enotone.library.OfflineLibraryGrouping

/** Подпись артиста едина в списке и на детальном экране: только готовый план из VM. */
@Composable
internal fun artistCountCaption(plan: ArtistDetailPlan): String {
    val songs = pluralStringResource(
        R.plurals.library_songs_count, plan.songs.size, plan.songs.size.toString(),
    )
    return if (plan.compilationsOnly) {
        stringResource(R.string.artist_compilations_only, songs)
    } else {
        val albums = pluralStringResource(
            R.plurals.library_albums_count, plan.albums.size, plan.albums.size.toString(),
        )
        "$albums · $songs"
    }
}

/** Строка вкладок «Артисты» и «Авторы альбомов»: без стрелки и меню (06/07). */
@Composable
internal fun LibraryPerformerRow(
    section: LibraryPage,
    group: OfflineLibraryGroup,
    artistPlan: ArtistDetailPlan?,
    albumCount: Int,
    onClick: () -> Unit,
) {
    val compilation = section == LibraryPage.ALBUM_ARTISTS &&
        group.key == OfflineLibraryGrouping.COMPILATIONS_KEY
    val caption = when {
        section == LibraryPage.ARTISTS -> artistCountCaption(
            artistPlan ?: ArtistDetailPlan(emptyList(), emptyList(), true),
        )
        compilation -> pluralStringResource(
            R.plurals.library_compilations_count, albumCount, albumCount.toString(),
        )
        else -> pluralStringResource(
            R.plurals.library_albums_count, albumCount, albumCount.toString(),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compilation) {
            Box(
                Modifier.size(48.dp).background(EnoColors.accentFill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(EnoIcons.ALBUM_ARTISTS.outline),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        } else {
            EnoLetterAvatar(name = group.name, contentDescription = group.name, size = 48.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyLarge,
                color = EnoColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = EnoColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider(
        color = EnoColors.line,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 64.dp),
    )
}
