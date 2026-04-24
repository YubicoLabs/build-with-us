package com.yubico.eap.quickstart.track.arkg

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.yubico.eap.quickstart.R
import com.yubico.eap.quickstart.helpers.DerivedPublicKey
import com.yubico.eap.quickstart.helpers.ellipsize
import com.yubico.eap.quickstart.helpers.times
import com.yubico.eap.quickstart.ui.YubicoGreen

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

@Preview
@Composable
private fun InProgressView() {
    val infiniteTransition = rememberInfiniteTransition(label = "infinite transition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 360.0f,
        label = "rotation",
        animationSpec = infiniteRepeatable(
            tween(1000),
            RepeatMode.Restart
        )
    )

    Box(
        modifier = Modifier
            .padding(48.dp)
            .fillMaxWidth()
            .rotate(rotation),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            fontSize = 100.sp,
            text = "⏳"
        )
    }
}

@Composable
private fun SuccessView(
    message: String,
    messageHash: ByteArray,
    signature: ByteArray,
    verified: Boolean,
    onFinished: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(32.dp),
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column {
                Text(
                    style = MaterialTheme.typography.headlineMedium,
                    text = stringResource(R.string.track_one_success_message_title)
                )
                Text(
                    style = MaterialTheme.typography.bodySmall,
                    text = message
                )
                Text(
                    style = MaterialTheme.typography.bodySmall,
                    text = messageHash.toHexString()
                )
                Text(
                    style = MaterialTheme.typography.headlineMedium,
                    text = stringResource(R.string.track_one_success_signature_title)
                )
                Text(
                    style = MaterialTheme.typography.bodySmall,
                    text = signature.toHexString()
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    style = MaterialTheme.typography.headlineMedium,
                    text = stringResource(R.string.track_one_success_verified_title)
                )
                Text(
                    modifier = Modifier.background(Color(if (verified) 0x3300FF00 else 0x33FF0000)),
                    style = MaterialTheme.typography.bodySmall,
                    text = if (verified) "Yes 👍" else "No 👎",
                )

                Row {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onFinished) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun SuccessViewPreview() {
    SuccessView(
        message = "HelloWorld",
        messageHash = "HelloWorld".toByteArray(),
        signature = "SIGNSTUAADS".toByteArray(),
        verified = true,
    ) { }
}

@Composable
private fun UserInformationView(
    title: String,
    message: String,
    informationItems: List<String>,
    confirmationButtonTitle: String = stringResource(android.R.string.cancel),
    onInformationSelected: ((index: Int) -> Unit)? = null,
    onCopyToClipBoard: ((String) -> Unit)? = null,
    onFinished: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFinished,
        confirmButton = {
            Button(onClick = onFinished) {
                Text(confirmationButtonTitle)
            }
        },
        dismissButton = {
            Row {
                onCopyToClipBoard?.let {
                    Button(
                        onClick = { it(informationItems.joinToString("\n")) },
                    ) {
                        Text(
                            modifier = Modifier.graphicsLayer {
                                colorFilter = ColorFilter.lighting(YubicoGreen, Color(0xFF000000))
                            },
                            text = "📋",
                        )
                    }
                }
            }
        },
        properties = DialogProperties(),
        title = { Text(text = title) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Text(
                        modifier = Modifier.padding(bottom = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        text = message
                    )
                }
                itemsIndexed(informationItems) { index, information ->
                    val color = if (index % 2 == 0) {
                        Color(0x20ffffff)
                    } else {
                        Color(0x10ffffff)
                    }

                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .fillMaxWidth()
                            .background(color),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            modifier = onInformationSelected?.let {
                                Modifier.clickable { onInformationSelected(index) }
                            } ?: Modifier,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            text = information
                        )
                    }
                }
            }
        }
    )
}
