package com.yubico.quickstart.track.signing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.yubico.quickstart.R
import com.yubico.quickstart.helpers.ellipsize
import com.yubico.quickstart.track.InProgressView
import com.yubico.quickstart.track.SuccessView
import com.yubico.quickstart.track.UserInformationView

@Composable
fun SigningTrackView(
    vm: SigningTrackViewModel,
    onCopyToClipBoard: (String) -> Unit,
    onFinished: () -> Unit,
) {
    val state by remember(vm) { vm.state }

    when (val typedState = state) {
        is SigningTrackViewModel.State.InProgress -> InProgressView(
            stringResource(R.string.general_waiting_for_yubikey)
        )

        is SigningTrackViewModel.State.Error -> UserInformationView(
            title = typedState.title,
            message = typedState.message,
            informationItems = typedState.logs,
            onInformationSelected = { onCopyToClipBoard(typedState.logs[it]) },
            onCopyToClipBoard = onCopyToClipBoard,
            onFinished = onFinished,
        )

        is SigningTrackViewModel.State.SignedAndVerified -> SuccessView(
            message = typedState.message.ellipsize(256),
            messageHash = typedState.messageHash,
            signature = typedState.signature,
            verified = typedState.verified,
            onFinished = onFinished,
        )
    }
}

