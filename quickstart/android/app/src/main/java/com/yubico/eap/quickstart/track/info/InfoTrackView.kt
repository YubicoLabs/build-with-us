package com.yubico.eap.quickstart.track.info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yubico.eap.quickstart.track.InProgressView
import com.yubico.eap.quickstart.track.UserInformationView

@Composable
fun InfoTrackView(
    vm: InfoTrackViewModel,
    onCopyToClipBoard: (String) -> Unit,
    onFinished: () -> Unit,
) {
    val state by remember(vm) { vm.state }

    when (val typedState = state) {
        is InfoTrackViewModel.State.InProgress -> InProgressView()

        is InfoTrackViewModel.State.Error -> UserInformationView(
            title = typedState.title,
            message = typedState.message,
            informationItems = typedState.logs,
            onInformationSelected = { onCopyToClipBoard(typedState.logs[it]) },
            onCopyToClipBoard = onCopyToClipBoard,
            onFinished = onFinished,
        )

        is InfoTrackViewModel.State.InformationRequested -> InformationView(
            typedState.information,
            onFinished = onFinished,
            copy = onCopyToClipBoard,
        )
    }
}

@Preview
@Composable
fun InformationViewPreview() {
    InformationView(
        information = InfoTrackViewModel.Information(
            versions = listOf("0", "1", "2", "3"),
            extensions = listOf("peter", "asd", "sam", "very long extension"),
            aaguid = byteArrayOf(),
            maxMsgSize = 0,
            options = mapOf("foo" to 1234, "bar" to 3133, "3" to ""),
            pinUvAuthProtocols = listOf(),
            maxCredentialCountInList = 0,
            maxCredentialIdLength = 0,
            transports = listOf(),
            algorithms = listOf(),
            maxSerializedLargeBlobArray = 0,
            forcePinChange = false,
            minPinLength = 0,
            firmwareVersion = 0,
            maxCredBlobLength = 0,
            maxRpidsForSetMinPinLength = 0,
            preferredPlatformUvAttempts = 0,
            uvModality = 0,
            certifications = mapOf(),
            remainingDiscoverableCredentials = 0,
            vendorPrototypeConfigCommands = listOf(),
            attestationFormats = listOf(),
            uvCountSinceLastPinEntry = 0,
            longTouchForReset = false,
            encIdentifier = byteArrayOf(),
            transportsForReset = listOf(),
            pinComplexityPolicy = false,
            pinComplexityPolicyUrl = byteArrayOf(),
            maxPinLength = 0,
            authenticatorConfigCommands = listOf(),
        ),
        {}
    ) {}
}

@Composable
fun InformationView(
    information: InfoTrackViewModel.Information,
    onFinished: () -> Unit,
    copy: (String) -> Unit,
) {
    Card(
        modifier = Modifier.padding(32.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            LazyColumn {
                item {
                    Text(
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.headlineLarge,
                        text = "Information"
                    )
                }

                // @formatter:off
                item { InformationRow("Versions", information.versions ) {copy(it)} }
                item { InformationRow("Extensions", information.extensions ) {copy(it)} }
                item { InformationRow("Aaguid", information.aaguid ) {copy(it)} }
                item { InformationRow("MaxMsgSize", information.maxMsgSize ) {copy(it)} }
                item { InformationRow("Options", information.options ) {copy(it)} }
                item { InformationRow("PinUvAuthProtocols", information.pinUvAuthProtocols ) {copy(it)} }
                item { InformationRow("MaxCredentialCountInList", information.maxCredentialCountInList ) {copy(it)} }
                item { InformationRow("MaxCredentialIdLength", information.maxCredentialIdLength ) {copy(it)} }
                item { InformationRow("Transports", information.transports ) {copy(it)} }
                item { InformationRow("Algorithms", information.algorithms ) {copy(it)} }
                item { InformationRow("MaxSerializedLargeBlobArray", information.maxSerializedLargeBlobArray ) {copy(it)} }
                item { InformationRow("ForcePinChange", information.forcePinChange ) {copy(it)} }
                item { InformationRow("MinPinLength", information.minPinLength ) {copy(it)} }
                item { InformationRow("FirmwareVersion", information.firmwareVersion ) {copy(it)} }
                item { InformationRow("MaxCredBlobLength", information.maxCredBlobLength ) {copy(it)} }
                item { InformationRow("MaxRpidsForSetMinPinLength", information.maxRpidsForSetMinPinLength ) {copy(it)} }
                item { InformationRow("PreferredPlatformUvAttempts", information.preferredPlatformUvAttempts ) {copy(it)} }
                item { InformationRow("UvModality", information.uvModality ) {copy(it)} }
                item { InformationRow("Certifications", information.certifications ) {copy(it)} }
                item { InformationRow("RemainingDiscoverableCredentials", information.remainingDiscoverableCredentials ) {copy(it)} }
                item { InformationRow("VendorPrototypeConfigCommands", information.vendorPrototypeConfigCommands ) {copy(it)} }
                item { InformationRow("AttestationFormats", information.attestationFormats ) {copy(it)} }
                item { InformationRow("UvCountSinceLastPinEntry", information.uvCountSinceLastPinEntry ) {copy(it)} }
                item { InformationRow("LongTouchForReset", information.longTouchForReset ) {copy(it)} }
                item { InformationRow("EncIdentifier", information.encIdentifier ) {copy(it)} }
                item { InformationRow("TransportsForReset", information.transportsForReset ) {copy(it)} }
                item { InformationRow("PinComplexityPolicy", information.pinComplexityPolicy ) {copy(it)} }
                item { InformationRow("PinComplexityPolicyUrl", information.pinComplexityPolicyUrl ) {copy(it)} }
                item { InformationRow("MaxPinLength", information.maxPinLength ) {copy(it)} }
                item { InformationRow("AuthenticatorConfigCommands", information.authenticatorConfigCommands ) {copy(it)} }
                // @formatter:on
            }
        }

        Row(
            modifier = Modifier.padding(4.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onFinished) { Text(stringResource(android.R.string.ok)) }
        }
    }
}

@Composable
private fun InformationRow(
    title: String,
    value: Any?,
    onCopyToClipBoard: (String) -> Unit
) {
    Row(
        modifier = Modifier.clickable {
            onCopyToClipBoard(
                "$title\n${
                    when (value) {
                        is ByteArray -> value.toHexString()
                        else -> value
                    }
                }"
            )
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            text = title
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .padding(horizontal = 8.dp)
                .background(MaterialTheme.colorScheme.onBackground)
        )

        when (value) {
            is List<*> ->
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    for (it in value) {
                        when (it) {
                            is HashMap<*, *> -> {
                                val line = it.map { (k, v) -> "$k: '$v'" }.joinToString(", ")
                                Text(
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    text = line
                                )
                            }

                            else ->
                                Text(
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    text = "$it"
                                )
                        }
                    }
                }

            is Map<*, *> ->
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    for (it in value.keys) {
                        Row {
                            Text(
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                text = "$it: '${value[it]}'"
                            )
                        }
                    }
                }

            is ByteArray ->
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    for (it in value.toHexString().chunked(32)) {
                        Text(
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            text = it
                        )
                    }
                }

            else ->
                Text(
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    text = "$value"
                )
        }
    }
    Spacer(modifier = Modifier.height(32.dp))
}
