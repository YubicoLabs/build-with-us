package com.yubico.eap.quickstart.track.ppuat

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.yubico.eap.quickstart.helpers.CredentialContainer
import com.yubico.eap.quickstart.helpers.DOMAIN
import com.yubico.eap.quickstart.helpers.sha256
import com.yubico.eap.quickstart.track.TrackViewModel
import com.yubico.yubikit.core.fido.CtapException
import com.yubico.yubikit.fido.android.ui.FidoClient
import com.yubico.yubikit.fido.ctap.ClientPin
import com.yubico.yubikit.fido.ctap.CredentialManagement
import com.yubico.yubikit.fido.ctap.Ctap2Session
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import com.yubico.eap.quickstart.logging.YOLOLogger as Log

class PpuatTrackViewModel(
    application: Application
) : TrackViewModel(application) {

    sealed class State {
        data class Error(
            val title: String,
            val message: String,
            val logs: List<String>
        ) : State()

        object InProgress : State()

        object NoTokenPresent : State()

        class ListCredentialsWithToken(
            val credentials: List<String>,
            val token: ByteArray,
        ) : State()
    }

    val state: MutableState<State> = mutableStateOf(State.InProgress)

    private var container: CredentialContainer? = null

    override suspend fun execute(client: FidoClient, activity: Activity) {
        viewModelScope.launch {
            clearLogs()
            state.value = State.InProgress

            container = CredentialContainer(activity)

            val storedToken = checkStorageForToken()
            if (storedToken != null) {
                // TODO: ADD tests, YOLO.

                // Token is stored securely, so we just display the creds, no pin needed!!
                // Essentially it's magic! 🪄
                container!!.getSessionWithoutPin(
                    failureCallback = {
                        state.value = State.Error(
                            "No session for you",
                            it.toString(),
                            Log.logs
                        )
                    },
                    successCallback = { session ->
                        showCredentials(
                            session,
                            storedToken
                        )
                    }
                )
            } else {
                state.value = State.NoTokenPresent
            }
        }
    }

    fun createToken() {
        viewModelScope.launch {
            state.value = State.InProgress

            container?.getSession(
                failureCallback = { th ->
                    state.value = State.Error(
                        "Error",
                        "Couldn't get session.\nReason: $th",
                        Log.logs
                    )
                },
                successCallback = { session, pinEntered ->
                    val pin = ClientPin(session, PinUvAuthProtocolV2())
                    try {
                        val token = pin.getPinToken(
                            pinEntered.toCharArray(),
                            ClientPin.PIN_PERMISSION_CM,
                            DOMAIN,
                        )

                        // TODO STORE TOKEN!💾
                        showCredentials(
                            session, token
                        )

                    } catch (e: CtapException) {
                        state.value = State.Error(
                            "Error",
                            e.toString(),
                            Log.logs
                        )
                    } finally {
                        session.close()
                    }
                },
            )
        }
    }

    private fun showCredentials(
        session: Ctap2Session,
        token: ByteArray
    ) {
        try {
            val management = CredentialManagement(
                session,
                PinUvAuthProtocolV2(),
                token
            )

            val rpIdHash = DOMAIN.toByteArray().sha256()
            val credentials = mutableListOf<CredentialManagement.CredentialData>()
            credentials.addAll(
                management.enumerateCredentials(
                    rpIdHash
                )
            )

            state.value = State.ListCredentialsWithToken(
                credentials.map {
                    """
                        ${it.user.getOrDefault("name", null)?:"{No Name}"}
                        ${(it.credentialId["id"] as? ByteArray)?.toHexString() ?: "{No Id}"}
                    """.trimIndent()
                },
                token
            )
        } catch (th: Throwable) {
            state.value = State.Error(
                "No listing of credentials with token for you.",
                "Why? Ask th:\n$th",
                Log.logs
            )
        }
    }

    fun deleteToken() {
        deleteStorageInToken()
        state.value = State.NoTokenPresent
    }

    private suspend fun checkStorageForToken(): ByteArray? {
        // TODO: Check storage for PPUAT
        delay(1.seconds)

        return null
    }

    private fun deleteStorageInToken() {
        viewModelScope.launch {
            // TODO: delete stored PPUAT
            delay(1.seconds)
        }
    }

    private fun clearLogs() {
        Log.logs.clear()

        (state.value as? State.Error)?.let { typedState ->
            state.value = typedState.copy(logs = Log.logs)
        }
    }
}
