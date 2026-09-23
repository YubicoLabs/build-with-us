package com.yubico.eap.quickstart.track.info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yubico.eap.quickstart.R
import com.yubico.eap.quickstart.helpers.Operation
import com.yubico.eap.quickstart.helpers.findAlgorithm
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
        is InfoTrackViewModel.State.InProgress -> InProgressView(
            stringResource(R.string.general_waiting_for_yubikey)
        )

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
        information = Operation.GetInfoOperation.Information(
            device = Operation.GetInfoOperation.Information.DeviceInformation(
                enabledCapabilities = 0,
                autoEjectTimeout = 1,
                challengeResponseTimeout = 2,
                deviceFlags = 3,
                nfcRestricted = true,
                serialNumber = 5,
                deviceVersion = "deviceVersion",
                deviceVersionQualifier = "deviceVersionQualifier",
                formFactor = "formFactor",
                supportedCapabilities = 47,
                isLocked = false,
                isFips = true,
                isSky = true,
                partNumber = "partNumber",
                fipsCapable = 0,
                fipsApproved = 3,
                pinComplexity = true,
                resetBlocked = 4,
                fpsVersion = "fpsVersion",
                stmVersion = "stmVersion",
            ),
            session = Operation.GetInfoOperation.Information.SessionInformation(
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
        ),
        onFinished = {},
        copy = {},
    )
}

@Composable
fun InformationView(
    information: Operation.GetInfoOperation.Information,
    onFinished: () -> Unit,
    copy: (String) -> Unit,
) {
    Card(
        modifier = Modifier.padding(32.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .weight(1f),
        ) {
            var showDeviceInfo: Boolean by remember { mutableStateOf(false) }
            var showSessionInfo: Boolean by remember { mutableStateOf(true) }

            Text(
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.headlineLarge,
                text = "Information"
            )

            LazyColumn {
                // @formatter:off
                item { InformationHeader("Device", showDeviceInfo) { showDeviceInfo = !showDeviceInfo} }
                if(showDeviceInfo) {
                    item { InformationRow("enabledCapabilities", information.device.enabledCapabilities, copy) }
                    item { InformationRow("autoEjectTimeout", information.device.autoEjectTimeout, copy) }
                    item { InformationRow("challengeResponseTimeout", information.device.challengeResponseTimeout, copy) }
                    item { InformationRow("deviceFlags", information.device.deviceFlags, copy) }
                    item { InformationRow("nfcRestricted", information.device.nfcRestricted, copy) }
                    item { InformationRow("serialNumber", information.device.serialNumber, copy) }
                    item { InformationRow("deviceVersion", information.device.deviceVersion, copy) }
                    item { InformationRow("deviceVersionQualifier", information.device.deviceVersionQualifier, copy) }
                    item { InformationRow("formFactor", information.device.formFactor, copy) }
                    item { InformationRow("supportedCapabilities", information.device.supportedCapabilities, copy) }
                    item { InformationRow("isLocked", information.device.isLocked, copy) }
                    item { InformationRow("isFips", information.device.isFips, copy) }
                    item { InformationRow("isSky", information.device.isSky, copy) }
                    item { InformationRow("partNumber", information.device.partNumber, copy) }
                    item { InformationRow("fipsCapable", information.device.fipsCapable, copy) }
                    item { InformationRow("fipsApproved", information.device.fipsApproved, copy) }
                    item { InformationRow("pinComplexity", information.device.pinComplexity, copy) }
                    item { InformationRow("resetBlocked", information.device.resetBlocked, copy) }
                    item { InformationRow("fpsVersion", information.device.fpsVersion, copy) }
                    item { InformationRow("stmVersion", information.device.stmVersion, copy) }
                }

                item { InformationHeader("Session", showSessionInfo) { showSessionInfo = !showSessionInfo} }
                if(showSessionInfo) {
                    item { InformationRow("versions", information.session.versions, copy) }
                    item { InformationRow("extensions", information.session.extensions, copy) }
                    item { InformationRow("aaguid", information.session.aaguid, copy) }
                    item { InformationRow("maxMsgSize", information.session.maxMsgSize, copy) }
                    item { InformationRow("options", information.session.options, copy) }
                    item { InformationRow("pinUvAuthProtocols", information.session.pinUvAuthProtocols, copy) }
                    item { InformationRow("maxCredentialCountInList", information.session.maxCredentialCountInList, copy) }
                    item { InformationRow("maxCredentialIdLength", information.session.maxCredentialIdLength, copy) }
                    item { InformationRow("transports", information.session.transports, copy) }
                    item { InformationRow("algorithms", information.session.algorithms, copy) }
                    item { InformationRow("maxSerializedLargeBlobArray", information.session.maxSerializedLargeBlobArray, copy) }
                    item { InformationRow("forcePinChange", information.session.forcePinChange, copy) }
                    item { InformationRow("minPinLength", information.session.minPinLength, copy) }
                    item { InformationRow("firmwareVersion", information.session.firmwareVersion, copy) }
                    item { InformationRow("maxCredBlobLength", information.session.maxCredBlobLength, copy) }
                    item { InformationRow("maxRpidsForSetMinPinLength", information.session.maxRpidsForSetMinPinLength, copy) }
                    item { InformationRow("preferredPlatformUvAttempts", information.session.preferredPlatformUvAttempts, copy) }
                    item { InformationRow("uvModality", information.session.uvModality, copy) }
                    item { InformationRow("certifications", information.session.certifications, copy) }
                    item { InformationRow("remainingDiscoverableCredentials", information.session.remainingDiscoverableCredentials, copy) }
                    item { InformationRow("vendorPrototypeConfigCommands", information.session.vendorPrototypeConfigCommands, copy) }
                    item { InformationRow("attestationFormats", information.session.attestationFormats, copy) }
                    item { InformationRow("uvCountSinceLastPinEntry", information.session.uvCountSinceLastPinEntry, copy) }
                    item { InformationRow("longTouchForReset", information.session.longTouchForReset, copy) }
                    item { InformationRow("encIdentifier", information.session.encIdentifier, copy) }
                    item { InformationRow("transportsForReset", information.session.transportsForReset, copy) }
                    item { InformationRow("pinComplexityPolicy", information.session.pinComplexityPolicy, copy) }
                    item { InformationRow("pinComplexityPolicyUrl", information.session.pinComplexityPolicyUrl, copy) }
                    item { InformationRow("maxPinLength", information.session.maxPinLength, copy) }
                    item { InformationRow("authenticatorConfigCommands", information.session.authenticatorConfigCommands, copy) }
                }
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
private fun InformationHeader(
    title: String,
    expanded: Boolean,
    clicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable {
                clicked()
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Monospace,
            text = title
        )
        Spacer(modifier = Modifier.weight(1f))

        val resource = if (expanded) {
            android.R.drawable.arrow_down_float
        } else {
            android.R.drawable.arrow_up_float
        }

        Icon(
            modifier = Modifier.padding(8.dp),
            painter = painterResource(resource),
            contentDescription = null,
        )
    }
}

@Composable
private fun InformationRow(
    title: String,
    value: Any?,
    onCopyToClipBoard: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clickable {
                onCopyToClipBoard(
                    "$title\n${
                        when (value) {
                            is ByteArray -> value.toHexString()
                            else -> value
                        }
                    }"
                )
            },
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            text = title,
        )

        when (value) {
            is List<*> ->
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    value.forEachIndexed { index, element ->
                        Row(
                            modifier = Modifier
                                .height(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BoxedLine(
                                when {
                                    value.size == 1 -> Cap.None
                                    index == 0 -> Cap.Start
                                    index < value.size - 1 -> Cap.Mid
                                    else -> Cap.End
                                }
                            )

                            when (element) {
                                is HashMap<*, *> -> {
                                    val line = if (element.isAlgorithmMap) {
                                        (element["alg"] as? Int)?.coseAlgorithmIdToReadable() ?: ""
                                    } else {
                                        element.map { (k, v) -> "$k: '$v'" }.joinToString(", ")
                                    }
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
                                        text = "$element"
                                    )
                            }
                        }
                    }
                }

            is Map<*, *> ->
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    value.keys.forEachIndexed { index, key ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BoxedLine(
                                when {
                                    value.size == 1 -> Cap.None
                                    index == 0 -> Cap.Start
                                    index < value.size - 1 -> Cap.Mid
                                    else -> Cap.End
                                }
                            )

                            Text(
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                text = "$key: '${value[key]}'"
                            )
                        }
                    }
                }

            is ByteArray -> {
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    val chunked = value.toHexString().chunked(16)
                    chunked.forEachIndexed { index, chunk ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BoxedLine(
                                when {
                                    chunked.size == 1 -> Cap.None
                                    index == 0 -> Cap.Start
                                    index < chunked.size - 1 -> Cap.Mid
                                    else -> Cap.End
                                }
                            )
                            Text(
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                text = chunk
                            )
                        }
                    }
                }
            }

            else -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoxedLine(Cap.None)

                    Text(
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        text = "$value"
                    )
                }
            }
        }
    }
}

sealed class Cap {
    object Start : Cap()
    object Mid : Cap()
    object End : Cap()

    object None : Cap()
}

@Composable
fun RowScope.BoxedLine(
    cap: Cap,
    leadColor: Color = MaterialTheme.colorScheme.onBackground,
    followerColor: Color = MaterialTheme.colorScheme.onBackground,
    verticalColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    val startColor = when (cap) {
        is Cap.None -> leadColor

        is Cap.Start -> leadColor
        is Cap.Mid -> Color.Transparent
        is Cap.End -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .width(8.dp)
            .height(0.5.dp)
            .background(startColor)
    )

    val topColor = when (cap) {
        is Cap.None -> Color.Transparent

        is Cap.Start -> Color.Transparent
        is Cap.Mid -> verticalColor
        is Cap.End -> verticalColor
    }

    val bottomColor = when (cap) {
        is Cap.None -> Color.Transparent
        is Cap.Start -> verticalColor
        is Cap.Mid -> verticalColor
        is Cap.End -> Color.Transparent
    }
    Column {
        Box(
            modifier = Modifier
                .width(0.5.dp)
                .height(8.dp)
                .background(topColor)
        )
        Box(
            modifier = Modifier
                .width(0.5.dp)
                .height(8.dp)
                .background(bottomColor)
        )
    }
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .weight(1f)
            .height(0.5.dp)
            .background(followerColor)
    )
}

private val HashMap<*, *>.isAlgorithmMap: Boolean
    get() = "type" in keys && get("type") == "public-key" &&
            "alg" in keys && get("alg") is Int

private fun Int.coseAlgorithmIdToReadable(): String = when (val algorithm = findAlgorithm(this)) {
    null -> "Unknown ($this)"
    else -> "${algorithm.name} (${algorithm.value})"
}
