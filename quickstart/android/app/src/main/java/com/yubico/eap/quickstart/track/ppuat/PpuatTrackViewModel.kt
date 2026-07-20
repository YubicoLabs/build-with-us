package com.yubico.eap.quickstart.track.ppuat

import android.app.Activity
import android.app.Application
import android.util.Base64.NO_PADDING
import android.util.Base64.NO_WRAP
import android.util.Base64.URL_SAFE
import android.util.Base64.encodeToString
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.yubico.eap.quickstart.helpers.CredentialContainer
import com.yubico.eap.quickstart.helpers.DOMAIN
import com.yubico.eap.quickstart.helpers.getClientOptions
import com.yubico.eap.quickstart.track.TrackViewModel
import com.yubico.yubikit.core.fido.CtapException
import com.yubico.yubikit.fido.android.ui.FidoClient
import com.yubico.yubikit.fido.client.Utils
import com.yubico.yubikit.fido.ctap.ClientPin
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
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

        open class TokenFound(
            open val token: ByteArray,
        ) : State()

        class ListCredentialsWithToken(
            val credentials: List<String>,
            override val token: ByteArray,
        ) : TokenFound(token)
    }

    val state: MutableState<State> = mutableStateOf(State.InProgress)

    private var container: CredentialContainer? = null

    override suspend fun execute(client: FidoClient, activity: Activity) {
        viewModelScope.launch {
            clearLogs()
            state.value = State.InProgress

            container = CredentialContainer(activity)

            val storedToken = checkStorageForToken()

            state.value = storedToken?.let { token ->
                State.TokenFound(token)
            } ?: State.NoTokenPresent
        }
    }

    fun createToken() {
        viewModelScope.launch {
            state.value = State.InProgress

            container?.getSession(
                failureCallback = { th ->
                    state.value = State.Error("Error", "Couldn't get session.\nReason: $th", Log.logs)
                },
                successCallback = { session ->
                    session.use { session ->
                        val pin = ClientPin(session, PinUvAuthProtocolV2())
                        try {
                            val token = pin.getUvToken(
                                Int.MAX_VALUE, // ALLOW ALL??
                                DOMAIN,
                                null
                            )

                            state.value = State.TokenFound(
                                token
                            )
                        } catch (e: CtapException) {
                            state.value = State.Error("Error", e.toString(), Log.logs)
                        }
                    }
                },
            )
        }
    }

    fun showCredentials() {
        viewModelScope.launch {
            if (state.value is State.TokenFound) {
                val tokenState = (state.value as State.TokenFound)
                state.value = State.InProgress

                container?.getSession(
                    failureCallback = { th ->
                        state.value = State.Error("Error", "$th", Log.logs)
                    },
                    successCallback = { session ->
                        val challenge = Random.nextBytes(32)
                        val clientData = getClientOptions(
                            type = "webauthn.get",
                            origin = DOMAIN,
                            challenge = encodeToString(
                                challenge,
                                NO_PADDING or NO_WRAP or URL_SAFE,
                            )
                        )

                        val clientDataHash = Utils.hash(clientData)

                        val credentials = session.getAssertions(
                            DOMAIN,
                            clientDataHash,
                            listOf(),
                            mapOf<String, Any?>(),
                            mapOf<String, Any?>(),
                            tokenState.token,
                            PinUvAuthProtocolV2.VERSION,
                            null
                        )

                        state.value = State.ListCredentialsWithToken(
                            credentials.map { "${it.credential}" },
                            tokenState.token
                        )
                    }
                )
            } else {
                state.value = State.Error("Error", "No token found.", Log.logs)
            }
        }
    }

    fun deleteToken() {
        if (state.value is State.TokenFound) {
            deleteStorageInToken()
            state.value = State.NoTokenPresent
        }
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
