package com.yubico.eap.quickstart.track.signing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.yubico.eap.quickstart.track.InProgressView
import com.yubico.eap.quickstart.track.SuccessView
import com.yubico.eap.quickstart.track.UserInformationView

@Composable
fun SigningTrackView(
    vm: SigningTrackViewModel,
    onCopyToClipBoard: (String) -> Unit,
    onFinished: () -> Unit,
) {
    val state by remember(vm) { vm.state }

    when (val typedState = state) {
        is SigningTrackViewModel.State.InProgress -> InProgressView()

        is SigningTrackViewModel.State.Error -> UserInformationView(
            title = typedState.title,
            message = typedState.message,
            informationItems = typedState.logs,
            onInformationSelected = { onCopyToClipBoard(typedState.logs[it]) },
            onCopyToClipBoard = onCopyToClipBoard,
            onFinished = onFinished,
        )

        is SigningTrackViewModel.State.SignedAndVerified -> SuccessView(
            message = typedState.message,
            messageHash = typedState.messageHash,
            signature = typedState.signature,
            verified = typedState.verified,
            onFinished = onFinished,
        )
    }
}

