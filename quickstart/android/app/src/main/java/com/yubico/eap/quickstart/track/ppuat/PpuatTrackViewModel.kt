package com.yubico.eap.quickstart.track.ppuat

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.yubico.eap.quickstart.helpers.CredentialContainer
import com.yubico.eap.quickstart.helpers.DOMAIN
import com.yubico.eap.quickstart.helpers.SecureStorage
import com.yubico.eap.quickstart.track.TrackViewModel
import com.yubico.yubikit.fido.android.ui.FidoClient
import kotlinx.coroutines.launch
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

        object WaitingForApp : State()

        object WaitingForUser : State()

        object NoTokenPresent : State()

        data class TokenPresent(
            val serialnumber: Int,
            val token: ByteArray
        ) : State()

        class ListCredentialsWithToken(
            val token: ByteArray,
            val credentials: List<String>,
        ) : State()
    }

    val state: MutableState<State> = mutableStateOf(State.WaitingForApp)

    private var container: CredentialContainer? = null

    private val secureStorage: SecureStorage = SecureStorage()

    override suspend fun execute(client: FidoClient, activity: Activity) {
        viewModelScope.launch {
            clearLogs()
            state.value = State.WaitingForApp

            container = CredentialContainer(activity)
            container?.getInfo(
                successCallback = { info ->
                    val serialNumber = info.device.serialNumber

                    val storedToken = checkStorageForToken(
                        serialNumber
                    )
                    state.value = if (storedToken != null && storedToken.isNotEmpty()) {
                        State.TokenPresent(
                            serialNumber,
                            storedToken
                        )
                    } else {
                        State.NoTokenPresent
                    }
                },
                failureCallback = {
                    state.value = State.NoTokenPresent
                }
            )
        }
    }

    fun createToken() {
        viewModelScope.launch {
            state.value = State.WaitingForUser

            container?.getPpuatToken(
                rpId = DOMAIN,
                failureCallback = { th ->
                    state.value = State.Error(
                        "Error",
                        "Couldn't get session.\nReason: $th",
                        Log.logs
                    )
                },
                successCallback = { serialNumber, token ->
                    storeToken(serialNumber, token)

                    state.value = State.TokenPresent(
                        serialNumber,
                        token
                    )
                },
            )
        }
    }

    fun showCredentialsWithToken(
        token: ByteArray
    ) {
        viewModelScope.launch {
            state.value = State.WaitingForUser

            container?.getCredentialsWithUvToken(
                token,
                successCallback = { credentials ->
                    try {
                        state.value = State.ListCredentialsWithToken(
                            token = token,
                            credentials = credentials.map {
                                """
                                ${it.user.getOrDefault("name", null) ?: "{No Name}"}
                                ${(it.credentialId["id"] as? ByteArray)?.toHexString() ?: "{No Id}"}
                            """.trimIndent()
                            }
                        )
                    } catch (th: Throwable) {
                        state.value = State.Error(
                            "Credential listing failed",
                            "Failed to list credentials using the token.\n\nReason: ${th.message ?: th}",
                            Log.logs
                        )
                    }
                },
                failureCallback = {
                    state.value = State.Error(
                        "No UV token credentials",
                        "Caught this exception:\n\n$it",
                        Log.logs
                    )
                }
            )
        }
    }

    fun deleteToken() {
        viewModelScope.launch {
            container?.getInfo(
                successCallback = { infoData ->
                    deleteStorageInToken(
                        infoData.device.serialNumber
                    )

                    state.value = State.NoTokenPresent
                },
                failureCallback = {},
            )
        }
    }

    private fun storeToken(
        serialnumber: Int,
        plain: ByteArray
    ) = try {
        secureStorage.store(
            application,
            serialnumber.toHexString(),
            plain
        )
    } catch (th: Throwable) {
        Log.e("WRITE", "Could not write secure .", th)
    }

    private fun checkStorageForToken(
        serialNumber: Int
    ): ByteArray? = try {
        secureStorage.retrieve(
            application,
            serialNumber.toHexString()
        )
    } catch (th: Throwable) {
        Log.e("CHECK", "Couldn't check secure file.", th)
        null
    }

    private fun deleteStorageInToken(
        serialNumber: Int
    ) = try {
        secureStorage.store(
            application,
            serialNumber.toHexString(),
            byteArrayOf()
        )
    } catch (th: Throwable) {
        Log.e("DELNO", "Couldn't delete secure file.", th)
    }

    private fun clearLogs() {
        Log.logs.clear()

        (state.value as? State.Error)?.let { typedState ->
            state.value = typedState.copy(logs = Log.logs)
        }
    }
}
