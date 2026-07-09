package com.yubico.eap.quickstart.track.info

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.yubico.eap.quickstart.track.TrackViewModel
import com.yubico.yubikit.fido.android.ui.FidoClient
import com.yubico.yubikit.fido.ctap.Ctap2Session
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialParameters
import kotlinx.coroutines.launch
import com.yubico.eap.quickstart.logging.YOLOLogger as Log

/// DON'T LOOK BEHIND THIS CURTAIN
private val Ctap2Session.InfoData.firmwareVersion: Int?
    get() = toString()
        .split(",")
        .firstOrNull { "firmwareVersion=" in it }
        ?.split("=")
        ?.last()
        ?.toIntOrNull()


class InfoTrackViewModel(
    application: Application
) : TrackViewModel(application) {

    data class Information(
        val versions: List<String>,
        val extensions: List<String>,
        val aaguid: ByteArray,
        val maxMsgSize: Int,
        val options: Map<String, Any?>,
        val pinUvAuthProtocols: List<Int>,
        val maxCredentialCountInList: Int?,
        val maxCredentialIdLength: Int?,
        val transports: List<String>,
        val algorithms: List<PublicKeyCredentialParameters>,
        val maxSerializedLargeBlobArray: Int,
        val forcePinChange: Boolean,
        val minPinLength: Int,
        val firmwareVersion: Int?,
        val maxCredBlobLength: Int,
        val maxRpidsForSetMinPinLength: Int,
        val preferredPlatformUvAttempts: Int?,
        val uvModality: Int,
        val certifications: Map<String, Any>,
        val remainingDiscoverableCredentials: Int?,
        val vendorPrototypeConfigCommands: List<Int>?,
        val attestationFormats: List<String>,
        val uvCountSinceLastPinEntry: Int?,
        val longTouchForReset: Boolean,
        val encIdentifier: ByteArray?,
        val transportsForReset: List<String>,
        val pinComplexityPolicy: Boolean?,
        val pinComplexityPolicyUrl: ByteArray?,
        val maxPinLength: Int,
        val authenticatorConfigCommands: List<Int>?,
    )

    sealed class State {
        data class InformationRequested(
            val information: Information
        ) : State()

        data class Error(
            val title: String,
            val message: String,
            val logs: List<String>
        ) : State()

        object InProgress : State()
    }

    val state: MutableState<State> = mutableStateOf(State.InProgress)

    override suspend fun execute(client: FidoClient, activity: Activity) {
        viewModelScope.launch {
            clearLogs()
            state.value = State.InProgress
            CredentialContainer(activity).getInfo { info ->
                state.value = State.InformationRequested(
                    information = Information(
                        versions = info.versions,
                        extensions = info.extensions,
                        aaguid = info.aaguid,
                        maxMsgSize = info.maxMsgSize,
                        options = info.options,
                        pinUvAuthProtocols = info.pinUvAuthProtocols,
                        maxCredentialCountInList = info.maxCredentialCountInList,
                        maxCredentialIdLength = info.maxCredentialIdLength,
                        transports = info.transports,
                        algorithms = info.algorithms,
                        maxSerializedLargeBlobArray = info.maxSerializedLargeBlobArray,
                        forcePinChange = info.forcePinChange,
                        minPinLength = info.minPinLength,
                        firmwareVersion = info.firmwareVersion,
                        maxCredBlobLength = info.maxCredBlobLength,
                        maxRpidsForSetMinPinLength = info.maxRpidsForSetMinPinLength,
                        preferredPlatformUvAttempts = info.preferredPlatformUvAttempts,
                        uvModality = info.uvModality,
                        certifications = info.certifications,
                        remainingDiscoverableCredentials = info.remainingDiscoverableCredentials,
                        vendorPrototypeConfigCommands = info.vendorPrototypeConfigCommands,
                        attestationFormats = info.attestationFormats,
                        uvCountSinceLastPinEntry = info.uvCountSinceLastPinEntry,
                        longTouchForReset = info.longTouchForReset,
                        encIdentifier = info.encIdentifier,
                        transportsForReset = info.transportsForReset,
                        pinComplexityPolicy = info.pinComplexityPolicy,
                        pinComplexityPolicyUrl = info.pinComplexityPolicyUrl,
                        maxPinLength = info.maxPinLength,
                        authenticatorConfigCommands = info.authenticatorConfigCommands,
                    )
                )
            }
        }
    }

    fun clearLogs() {
        Log.logs.clear()

        (state.value as? State.Error)?.let { typedState ->
            state.value = typedState.copy(logs = Log.logs)
        }
    }
}
