package com.yubico.quickstart.track.arkg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.yubico.quickstart.R
import com.yubico.quickstart.helpers.DerivedPublicKey
import com.yubico.quickstart.helpers.ellipsize
import com.yubico.quickstart.helpers.times
import com.yubico.quickstart.track.InProgressView
import com.yubico.quickstart.track.SuccessView
import com.yubico.quickstart.track.UserInformationView

@Composable
fun ARKGTrackView(
    vm: ARKGTrackViewModel,
    onCopyToClipBoard: (String) -> Unit,
    onFinished: () -> Unit,
) {
    val state by remember(vm) { vm.state }

    when (val typedState = state) {
        is ARKGTrackViewModel.State.InProgress -> InProgressView(
            stringResource(R.string.general_waiting_for_yubikey)
        )

        is ARKGTrackViewModel.State.Error -> UserInformationView(
            title = typedState.title,
            message = typedState.message,
            informationItems = typedState.logs,
            onInformationSelected = { onCopyToClipBoard(typedState.logs[it]) },
            onCopyToClipBoard = onCopyToClipBoard,
            onFinished = onFinished,
        )

        is ARKGTrackViewModel.State.PublicKeysDerived -> UserInformationView(
            title = stringResource(R.string.track_one_select_public_key_title),
            message = stringResource(R.string.track_one_select_public_key_message),
            informationItems = typedState.keys.toInformationStrings(),
            onInformationSelected = {
                vm.createSignatureWithKey(typedState.keys[it])
            },
            onFinished = onFinished
        )

        is ARKGTrackViewModel.State.SignatureCreated -> SuccessView(
            message = typedState.message,
            messageHash = typedState.messageHash,
            signature = typedState.signature,
            verified = typedState.verified,
            onFinished = onFinished,
        )
    }
}

private fun List<DerivedPublicKey>.toInformationStrings() = mapIndexed { index, it ->
    val label = "Key ${"%02d".format(index)} "
    val maxCharacters = 12
    val publicKey = it.publicKey.toHexString().ellipsize(maxCharacters)
    val handle = it.keyHandle.toHexString().ellipsize(maxCharacters)

    "$label┬(K)> $publicKey\n${" " * label.length}└(H)> $handle"
}

