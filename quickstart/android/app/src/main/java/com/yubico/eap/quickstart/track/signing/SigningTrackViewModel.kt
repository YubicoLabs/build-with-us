package com.yubico.eap.quickstart.track.signing

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yubico.eap.quickstart.helpers.SignExtensionName
import com.yubico.eap.quickstart.helpers.encode64
import com.yubico.eap.quickstart.helpers.extractCredentialId
import com.yubico.eap.quickstart.helpers.extractKeyHandle
import com.yubico.eap.quickstart.helpers.extractPublicKey
import com.yubico.eap.quickstart.helpers.extractSignature
import com.yubico.eap.quickstart.helpers.sha256
import com.yubico.eap.quickstart.math.Arkg.verifySignature
import com.yubico.eap.quickstart.track.TrackViewModel
import com.yubico.yubikit.fido.android.ui.FidoClient
import com.yubico.yubikit.fido.android.ui.Origin
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random
import com.yubico.eap.quickstart.logging.YOLOLogger as Log

class SigningTrackViewModel(
    application: Application
) : TrackViewModel(application) {

    sealed class State {
        data class SignedAndVerified(
            val message: String,
            val messageHash: ByteArray,
            val signature: ByteArray,
            val verified: Boolean
        ) : State()

        data class Error(
            val title: String,
            val message: String,
            val logs: List<String>
        ) : State()

        object InProgress : State()
    }

    val state: MutableState<State> = mutableStateOf(State.InProgress)

    lateinit var client: FidoClient

    override suspend fun execute(client: FidoClient) {
        clearLogs()

        this.client = client

        val domain = "demo.yubico.com"
        val challenge = Random.nextBytes(32).encode64()
        val sampleUserId: String = UUID.randomUUID().toString()

        // TODO: ADJUST KEY GEN ALGORITHM ID
        val requestJson = """
        {
            "challenge": "$challenge",
            "rp": {
                "id": "$domain", 
                "name": "Yubico Demo"
            },
            "user": {
                "id": "$sampleUserId",
                "name": "eap@yubico.com",
                "displayName": "Early Access Program"
            },
            "pubKeyCredParams": [
                {
                    "type": "public-key",
                    "alg": -7
                }
            ],
            "authenticatorSelection": {
              "authenticatorAttachment": "cross-platform",
              "residentKey": "discouraged",
              "userVerification": "discouraged",
              "requireResidentKey": false
            },
            "attestation": "none",
            "extensions": {
                "$SignExtensionName": {
                    "generateKey": {
                        "algorithms": [
                             -65600,
                             -65539, 
                             -9, 
                             -7 
                        ]
                    }
                }
            }
        }
        """.trimIndent()

        val credentialResult = client.makeCredential(
            origin = Origin("https://$domain"),
            request = requestJson,
            clientDataHash = null, // let the SDK calculate
        )

        if (!credentialResult.isSuccess) {
            val throwable = credentialResult.exceptionOrNull()
            Log.e("ERROR", "Error while processing track one.", throwable)

            state.value = State.Error(
                title = throwable?.javaClass?.simpleName ?: "ERROR",
                message = throwable.toString(),
                Log.logs
            )
        } else {
            val credential = JSONObject(credentialResult.getOrThrow())
            val message = "Hello World" // TODO: Think if user should input data here now.

            signAndVerify(credential, message)
        }
    }

    private fun signAndVerify(credential: JSONObject, message: String) {
        state.value = State.InProgress

        val messageSha256 = message.toByteArray().sha256()

        val domain = "demo.yubico.com"
        val challenge = Random.nextBytes(32).encode64()

        val credentialId = credential.extractCredentialId()
        val keyHandle = credential.extractKeyHandle() ?: throw IllegalStateException("key handle not found.")
        val publicKey = credential.extractPublicKey() ?: throw IllegalStateException("public key not found.")

        val getRequest = """ {
            "challenge": "$challenge",
            "rpId": "$domain",
            "allowCredentials" : [{
                "type": "public-key",
                "id": "$credentialId"
            }],
            "userVerification": "discouraged",
            "extensions": {
                "$SignExtensionName": {
                    "signByCredential": {
                        "$credentialId": {
                            "keyHandle": "$keyHandle",
                            "tbs": "${messageSha256.encode64()}",
                        }
                    }
                }
            }
        }
        """.trimIndent()

        viewModelScope.launch {
            Log.i("CREDGET", "REQUEST_JSON")
            Log.i("CREDGET", getRequest)

            val result = client.getAssertion(
                origin = Origin("https://$domain"),
                request = getRequest,
                clientDataHash = null,
            )

            Log.i("RES", result.toString())

            state.value = if (result.isFailure) {
                val throwable = result.exceptionOrNull()
                Log.e("ERROR", "Error while processing track one's signing.", throwable)

                State.Error(
                    title = throwable?.javaClass?.simpleName ?: "ERROR",
                    message = throwable.toString(),
                    Log.logs.reversed()
                )
            } else {
                JSONObject(
                    result.getOrThrow()
                ).extractSignature()?.let { signature ->
                    val verified = verifySignature(
                        publicKey,
                        messageSha256,
                        signature
                    )

                    State.SignedAndVerified(
                        message = message,
                        messageHash = messageSha256,
                        signature,
                        verified
                    )
                } ?: State.Error(
                    "No signature found.",
                    "Sadly signature is not a part of the result: $result",
                    Log.logs.reversed()
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
