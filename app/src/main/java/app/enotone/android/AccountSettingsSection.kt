package app.enotone.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Pure account UI; previews never instantiate the vault or hit WebDAV. */
@Composable
internal fun AccountSettingsSection(
    account: AccountUiState,
    showFields: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onVerify: () -> Unit,
    onRequestSignOut: () -> Unit,
    onConfirmSignOut: () -> Unit,
    onCancelSignOut: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showFields || !account.loggedIn) {
            OutlinedTextField(
                value = account.email, onValueChange = onEmailChange,
                label = { Text(stringResource(R.string.email)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                value = account.password, onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.app_password)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Text(stringResource(R.string.credential_vault_explanation),
                style = MaterialTheme.typography.bodySmall)
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !account.busy &&
                    account.email.isNotBlank() && account.password.isNotBlank(),
                onClick = onVerify,
            ) {
                Text(stringResource(if (account.checking)
                    R.string.account_checking else R.string.account_verify))
            }
        }
        if (account.loggedIn) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !account.busy, onClick = onRequestSignOut,
            ) { Text(stringResource(R.string.account_sign_out)) }
        }
        if (account.restoring) {
            Text(stringResource(R.string.account_restoring))
        } else if (account.signingOut) {
            Text(stringResource(R.string.account_signing_out))
        } else if (account.loggedIn && account.error == null) {
            Text(stringResource(R.string.credential_vault_restored))
        }
        when (account.error) {
            AccountError.RESTORE -> Text(stringResource(R.string.credential_vault_failed))
            AccountError.CONNECTION -> Text(stringResource(R.string.account_verification_failed))
            AccountError.STORAGE -> Text(stringResource(R.string.credential_vault_failed))
            AccountError.OTHER_ACCOUNT -> Text(stringResource(R.string.account_other_account))
            AccountError.SIGN_OUT -> Text(stringResource(R.string.account_sign_out_failed))
            null -> Unit
        }
    }

    if (account.showConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelSignOut,
            title = { Text(stringResource(R.string.account_sign_out_title)) },
            text = {
                Text(stringResource(R.string.account_sign_out_confirm, account.trackCount))
            },
            confirmButton = {
                TextButton(onClick = onConfirmSignOut) {
                    Text(stringResource(R.string.account_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelSignOut) {
                    Text(stringResource(R.string.album_refresh_cancel))
                }
            },
        )
    }
}

@Preview(name = "Аккаунт — не вошёл", showBackground = true)
@Composable
private fun AccountLoggedOutPreview() {
    EnoToneTheme { PreviewAccount(AccountUiState(restoring = false)) }
}

@Preview(name = "Аккаунт — проверка", showBackground = true)
@Composable
private fun AccountCheckingPreview() {
    EnoToneTheme {
        PreviewAccount(AccountUiState(
            email = "example@invalid.test", password = "password",
            restoring = false, checking = true,
        ))
    }
}

@Preview(name = "Аккаунт — вошёл", showBackground = true)
@Composable
private fun AccountLoggedInPreview() {
    EnoToneTheme {
        PreviewAccount(AccountUiState(
            email = "example@invalid.test", restoring = false, loggedIn = true,
        ))
    }
}

@Preview(name = "Аккаунт — ошибка", showBackground = true)
@Composable
private fun AccountErrorPreview() {
    EnoToneTheme {
        PreviewAccount(AccountUiState(
            email = "example@invalid.test", restoring = false,
            error = AccountError.CONNECTION,
        ))
    }
}

@Composable
private fun PreviewAccount(account: AccountUiState) {
    AccountSettingsSection(account, true, {}, {}, {}, {}, {}, {})
}
