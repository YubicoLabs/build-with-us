package com.yubico.eap.quickstart.track.arkg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.yubico.eap.quickstart.R
import com.yubico.eap.quickstart.helpers.DerivedPublicKey
import com.yubico.eap.quickstart.helpers.ellipsize
import com.yubico.eap.quickstart.helpers.times
import com.yubico.eap.quickstart.track.InProgressView
import com.yubico.eap.quickstart.track.SuccessView
import com.yubico.eap.quickstart.track.UserInformationView

@Composable
fun ARKGTrackView(
    vm: ARKGTrackViewModel,
    onCopyToClipBoard: (String) -> Unit,
    onFinished: () -> Unit,
) {
    val state by remember(vm) { vm.state }

    when (val typedState = state) {
        is ARKGTrackViewModel.State.InProgress -> InProgressView()

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

