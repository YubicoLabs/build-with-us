package com.yubico.eap.quickstart.track.ppuat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.yubico.eap.quickstart.R
import com.yubico.eap.quickstart.track.InProgressView
import com.yubico.eap.quickstart.track.UserInformationView

@Composable
fun PpuatTrackView(
    vm: PpuatTrackViewModel,
    onCopyToClipBoard: (String) -> Unit,
    onFinished: () -> Unit,
) {
    val state by remember { vm.state }

    when (val typedState = state) {
        is PpuatTrackViewModel.State.WaitingForUser -> InProgressView(
            stringResource(R.string.general_waiting_for_yubikey)
        )

        is PpuatTrackViewModel.State.WaitingForApp -> InProgressView(
            stringResource(R.string.general_waiting_for_yubikey)
        )

        is PpuatTrackViewModel.State.NoTokenPresent -> UserInformationView(
            title = "No Token Found",
            message = "Please continue to create a token.",
            confirmationButtonTitle = "Create Token",
            onConfirm = vm::createToken,
        )

        is PpuatTrackViewModel.State.TokenPresent -> UserInformationView(
            title = "Stored token found",
            message = "Shall we check the credentials with that token?",
            confirmationButtonTitle = "Enumerate Credentials with token",
            onConfirm = { vm.showCredentialsWithToken(typedState.token) },
        )

        is PpuatTrackViewModel.State.ListCredentialsWithToken -> UserInformationView(
            title = "Credentials Found",
            message = "The following credentials where found for token ${typedState.token.toHexString()}:",
            informationItems = typedState.credentials,
            confirmationButtonTitle = "Delete Token",
            onConfirm = vm::deleteToken,
            onFinished = onFinished,
            onInformationSelected = {
                onCopyToClipBoard(
                    typedState.credentials[it]
                )
            }
        )

        is PpuatTrackViewModel.State.Error -> UserInformationView(
            title = typedState.title,
            message = typedState.message,
            informationItems = typedState.logs,
            onInformationSelected = { onCopyToClipBoard(typedState.logs[it]) },
            onCopyToClipBoard = onCopyToClipBoard,
            onConfirm = vm::deleteToken,
            confirmationButtonTitle = "Delete Token?",
            onFinished = onFinished,
        )
    }
}
