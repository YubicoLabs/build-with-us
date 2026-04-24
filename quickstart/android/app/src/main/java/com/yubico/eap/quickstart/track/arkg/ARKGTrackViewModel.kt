package com.yubico.eap.quickstart.track.arkg

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yubico.eap.quickstart.helpers.DerivedPublicKey
import com.yubico.eap.quickstart.helpers.SignExtensionName
import com.yubico.eap.quickstart.helpers.SigningResponse
import com.yubico.eap.quickstart.helpers.encode64
import com.yubico.eap.quickstart.helpers.extractCredentialId
import com.yubico.eap.quickstart.helpers.extractKeyHandle
import com.yubico.eap.quickstart.helpers.extractSignature
import com.yubico.eap.quickstart.helpers.extractSigningResponse
import com.yubico.eap.quickstart.helpers.sha256
import com.yubico.eap.quickstart.math.Arkg
import com.yubico.eap.quickstart.math.Arkg.verifySignature
import com.yubico.yubikit.fido.Cbor
import com.yubico.yubikit.fido.android.ui.FidoClient
import com.yubico.yubikit.fido.android.ui.Origin
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random
import com.yubico.eap.quickstart.logging.YOLOLogger as Log

class ARKGTrackViewModel(
    application: Application
) : AndroidViewModel(application) {

    sealed class State {
        data class PublicKeysDerived(
            val credential: JSONObject,
            val keys: List<DerivedPublicKey>
        ) : State()

        data class SignatureCreated(
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

    suspend fun execute(client: FidoClient) {
        clearLogs()

        this.client = client

        val domain = "demo.yubico.com"
        val challenge = Random.nextBytes(32).encode64()
        val sampleUserId: String = UUID.randomUUID().toString()

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

        state.value = if (!credentialResult.isSuccess) {
            val throwable = credentialResult.exceptionOrNull()
            Log.e("ERROR", "Error while processing track one.", throwable)

            State.Error(
                title = throwable?.javaClass?.simpleName ?: "ERROR",
                message = throwable.toString(),
                Log.logs
            )
        } else {
            val credential = JSONObject(credentialResult.getOrThrow())
            derivePublicKeysFromCredential(credential)
        }
    }

    private fun derivePublicKeysFromCredential(credential: JSONObject): State {
        val pretty = credential.toString(2)
        val message = "Credential created:\n$pretty"
        Log.d("PKs", message)

        return try {
            val response = credential.extractSigningResponse()
            val keys = response.derivePublicKeys(10)

            Log.d("PKs", keys.toString())

            State.PublicKeysDerived(
                credential = credential,
                keys = keys
            )
        } catch (th: Throwable) {
            val message = "Error while deriving."
            Log.e("DERIVE", message, th)

            State.Error(th.toString(), message, Log.logs.reversed())
        }
    }

    fun createSignatureWithKey(derivedKey: DerivedPublicKey) {
        if (state.value !is State.PublicKeysDerived) {
            return
        }

        val message = "Hello World"
        val messageSha256 = message.toByteArray().sha256()

        val domain = "demo.yubico.com"
        val challenge = Random.nextBytes(32).encode64()

        val credentialId = (state.value as State.PublicKeysDerived).credential.extractCredentialId()

        val keyHandle =
            (state.value as State.PublicKeysDerived).credential.extractKeyHandle()

        if (keyHandle == null) {
            val errorMessage = "Missing key handle in credential."
            Log.e("ERROR", errorMessage)
            state.value = State.Error(
                title = "Missing key handle",
                message = errorMessage,
                logs = Log.logs.reversed()
            )
            return
        }

        val args = Cbor.encode(
            mapOf<Int, Any>(
                3 to -65539,
                -3 to -65700,
                -2 to derivedKey.context,
                -1 to derivedKey.keyHandle
            )
        )

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
                            "additionalArgs": "${args.encode64()}"
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
                        derivedKey.publicKey,
                        messageSha256,
                        signature
                    )

                    State.SignatureCreated(
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

private fun SigningResponse.derivePublicKeys(amount: Int): List<DerivedPublicKey> {
    if (type != -65537) {
        throw UnsupportedOperationException("Elliptic Curve type $type not supported.")
    }

    if (algorithm != -65700) {
        throw UnsupportedOperationException("Algorithm $algorithm not supported.")
    }

    return (0..<amount).map { index ->
        val ikm = Random.nextBytes(64)
        val context = "TrackOneAndroidContext$index".toByteArray()

        val (key, handle) = Arkg.deriveArkgPublicKey(
            encapsulationPoint,
            blindingPoint,
            ikm,
            context
        )

        DerivedPublicKey(context, key, handle)
    }
}
