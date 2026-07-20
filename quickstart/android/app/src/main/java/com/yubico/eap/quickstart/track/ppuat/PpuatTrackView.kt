package com.yubico.eap.quickstart.track.ppuat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
        is PpuatTrackViewModel.State.InProgress -> InProgressView()

        is PpuatTrackViewModel.State.NoTokenPresent -> UserInformationView(
            title = "No Token Found",
            message = "Please continue to create a token.",
            confirmationButtonTitle = "Create Token",
            onConfirm = vm::createToken,
        )

        is PpuatTrackViewModel.State.ListCredentialsWithToken -> UserInformationView(
            title = "Credentials Found",
            message = "The following credentials where found for token ${typedState.token.toHexString()}.",
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

        is PpuatTrackViewModel.State.TokenFound -> UserInformationView(
            title = "Token Created",
            message = "The following token was created.",
            informationItems = listOf(typedState.token.toHexString()),
            confirmationButtonTitle = "Retrieve Credentials",
            onConfirm = vm::showCredentials,
            onFinished = onFinished,
            onInformationSelected = {
                onCopyToClipBoard(
                    typedState.token.toHexString()
                )
            }
        )

        is PpuatTrackViewModel.State.Error -> UserInformationView(
            title = typedState.title,
            message = typedState.message,
            informationItems = typedState.logs,
            onInformationSelected = { onCopyToClipBoard(typedState.logs[it]) },
            onCopyToClipBoard = onCopyToClipBoard,
            onFinished = onFinished,
        )
    }
}
