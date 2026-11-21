package app.enotone.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enotone.android.core.ui.component.BanditPose
import app.enotone.android.core.ui.component.EnoErrorSheet
import app.enotone.android.core.ui.component.EnoStateAction
import app.enotone.android.core.ui.component.EnoStateView
import app.enotone.android.core.ui.theme.EnoColors
import java.text.NumberFormat

/** First-run screens only; the regular app and D6-F player sheet stay in their own shell. */
@Composable
internal fun FirstRunExperience(
    phase: FirstRunPhase,
    account: AccountUiState,
    indexing: LibraryIndexingUiState,
    songCount: Int,
    albumCount: Int,
    artistCount: Int,
    error: FirstRunError?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    onListenWhileReading: () -> Unit,
    onRetryNow: () -> Unit,
    onGoToMusic: () -> Unit,
    onErrorAction: (FirstRunError) -> Unit,
    onErrorDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        when (phase) {
            FirstRunPhase.LOGIN -> FirstRunLogin(
                account = account,
                onEmailChange = onEmailChange,
                onPasswordChange = onPasswordChange,
                onConnect = onConnect,
            )
            FirstRunPhase.INDEXING -> FirstRunIndexing(
                indexing = indexing,
                onListenWhileReading = onListenWhileReading,
            )
            FirstRunPhase.INDEXING_OFFLINE -> FirstRunSleeping(
                indexing = indexing,
                onRetryNow = onRetryNow,
            )
            FirstRunPhase.DONE -> FirstRunDone(
                songCount = songCount,
                albumCount = albumCount,
                artistCount = artistCount,
                onGoToMusic = onGoToMusic,
            )
            FirstRunPhase.APP -> Unit
        }
        error?.let { shownError ->
            val (message, action) = shownError.resources()
            EnoErrorSheet(
                message = stringResource(message),
                actionLabel = stringResource(action),
                onAction = { onErrorAction(shownError) },
                onDismiss = onErrorDismiss,
            )
        }
    }
}

@Composable
private fun FirstRunLogin(
    account: AccountUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    EnoStateView(
        pose = BanditPose.SITTING,
        title = stringResource(R.string.first_run_login_title),
        message = stringResource(R.string.first_run_login_message),
        primaryAction = EnoStateAction(
            label = stringResource(R.string.first_run_connect),
            onClick = onConnect,
            fillWidth = true,
            enabled = !account.signOutFailed,
        ),
        modifier = Modifier.fillMaxSize().padding(top = LOGIN_TOP_PADDING),
        content = {
            FirstRunLoginFields(account, onEmailChange, onPasswordChange)
        },
    )
}

@Composable
private fun ColumnScope.FirstRunLoginFields(
    account: AccountUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FirstRunField(
            value = account.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.first_run_email),
            enabled = !account.busy && !account.signOutFailed,
            readOnly = account.reauthenticationEmail != null,
        )
        FirstRunField(
            value = account.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.first_run_app_password),
            password = true,
            enabled = !account.busy && !account.signOutFailed,
        )
        when (account.error) {
            AccountError.RESTORE, AccountError.STORAGE ->
                Text(stringResource(R.string.credential_vault_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            AccountError.OTHER_ACCOUNT ->
                Text(stringResource(R.string.account_other_account),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            AccountError.SIGN_OUT ->
                Text(stringResource(R.string.account_sign_out_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            AccountError.CONNECTION,
            AccountError.CREDENTIALS_REJECTED,
            null -> Unit
        }
    }
}

@Composable
private fun FirstRunField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    password: Boolean = false,
    readOnly: Boolean = false,
) {
    val shape = RoundedCornerShape(FIELD_RADIUS)
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, EnoColors.line, shape)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = EnoColors.textSecondary,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = EnoColors.text,
                fontWeight = FontWeight.Normal,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (password) PasswordVisualTransformation()
                else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Email,
            ),
            modifier = Modifier.fillMaxWidth()
                .padding(top = 1.dp)
                .testTag(if (password) FIRST_RUN_PASSWORD_FIELD else FIRST_RUN_EMAIL_FIELD)
                .semantics { contentDescription = label },
        )
    }
}

@Composable
private fun FirstRunIndexing(
    indexing: LibraryIndexingUiState,
    onListenWhileReading: () -> Unit,
) {
    val progress = indexing.status.progress
    val firstRunProgress = calculateFirstRunProgress(
        indexing.importedTrackCount,
        indexing.pendingTagCount,
        progress,
    )
    val completed = firstRunProgress.completed
    val total = firstRunProgress.total
    val fraction = if (total == 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
    val locale = LocalConfiguration.current.locales[0]
    val numberFormat = NumberFormat.getIntegerInstance(locale)
    val progressText = stringResource(
        R.string.first_run_progress,
        numberFormat.format(completed),
        numberFormat.format(total),
    )

    EnoStateView(
        pose = BanditPose.READING,
        title = stringResource(R.string.first_run_indexing_title),
        message = progressText,
        secondaryAction = EnoStateAction(
            label = stringResource(R.string.first_run_listen_while_reading),
            onClick = onListenWhileReading,
        ),
        modifier = Modifier.fillMaxSize().padding(top = INDEXING_TOP_PADDING),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(PROGRESS_HEIGHT)
                        .testTag(FIRST_RUN_PROGRESS),
                    color = EnoColors.accentFill,
                    trackColor = EnoColors.line,
                )
            }
        },
    )
}

@Composable
private fun FirstRunSleeping(
    indexing: LibraryIndexingUiState,
    onRetryNow: () -> Unit,
) {
    val progress = indexing.status.progress
    val firstRunProgress = calculateFirstRunProgress(
        indexing.importedTrackCount,
        indexing.pendingTagCount,
        progress,
    )
    val locale = LocalConfiguration.current.locales[0]
    val numberFormat = NumberFormat.getIntegerInstance(locale)

    EnoStateView(
        pose = BanditPose.SLEEPING_BOOKS,
        title = stringResource(R.string.first_run_sleeping_title),
        message = stringResource(
            R.string.first_run_sleeping_message,
            numberFormat.format(firstRunProgress.completed),
            numberFormat.format(firstRunProgress.total),
        ),
        primaryAction = EnoStateAction(
            label = stringResource(R.string.first_run_retry_now),
            onClick = onRetryNow,
        ),
        modifier = Modifier.fillMaxSize().padding(top = INDEXING_TOP_PADDING),
    )
}

@Composable
private fun FirstRunDone(
    songCount: Int,
    albumCount: Int,
    artistCount: Int,
    onGoToMusic: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val numberFormat = NumberFormat.getIntegerInstance(locale)
    val songs = pluralStringResource(
        R.plurals.first_run_song_count, songCount, numberFormat.format(songCount),
    )
    val albums = pluralStringResource(
        R.plurals.first_run_album_count, albumCount, numberFormat.format(albumCount),
    )
    val artists = pluralStringResource(
        R.plurals.first_run_artist_count, artistCount, numberFormat.format(artistCount),
    )
    EnoStateView(
        pose = BanditPose.HAPPY,
        title = stringResource(R.string.first_run_done_title),
        message = stringResource(R.string.first_run_summary, songs, albums, artists),
        primaryAction = EnoStateAction(
            label = stringResource(R.string.first_run_go_to_music),
            onClick = onGoToMusic,
        ),
        modifier = Modifier.fillMaxSize().padding(top = DONE_TOP_PADDING),
    )
}

private fun FirstRunError.resources(): Pair<Int, Int> = when (this) {
    FirstRunError.LOGIN_CREDENTIALS ->
        R.string.first_run_error_login_credentials to R.string.first_run_error_enter_again
    FirstRunError.CLOUD_UNAVAILABLE ->
        R.string.first_run_error_cloud_unavailable to R.string.first_run_error_retry
    FirstRunError.INDEXING_AUTHENTICATION ->
        R.string.first_run_error_auth_expired to R.string.first_run_error_sign_in_again
    FirstRunError.INDEXING_FILES ->
        R.string.first_run_error_files to R.string.first_run_error_retry
}

private val LOGIN_TOP_PADDING = 96.dp
private val INDEXING_TOP_PADDING = 150.dp
private val DONE_TOP_PADDING = 150.dp
private val FIELD_RADIUS = 18.dp
private val PROGRESS_HEIGHT = 8.dp

internal const val FIRST_RUN_EMAIL_FIELD = "first_run_email_field"
internal const val FIRST_RUN_PASSWORD_FIELD = "first_run_password_field"
internal const val FIRST_RUN_PROGRESS = "first_run_progress"
