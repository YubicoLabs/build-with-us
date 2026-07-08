package com.yubico.eap.quickstart.track.arkg

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.yubico.eap.quickstart.helpers.DerivedPublicKey
import com.yubico.eap.quickstart.helpers.encode64
import com.yubico.eap.quickstart.helpers.extractSignature
import com.yubico.eap.quickstart.helpers.sha256
import com.yubico.eap.quickstart.math.Arkg.verifySignature
import com.yubico.eap.quickstart.math.createSignCredentialCreateOption
import com.yubico.eap.quickstart.math.createSigningAttestationOption
import com.yubico.eap.quickstart.math.derivePublicKeys
import com.yubico.eap.quickstart.track.TrackViewModel
import com.yubico.yubikit.fido.android.ui.FidoClient
import com.yubico.yubikit.fido.android.ui.Origin
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random
import com.yubico.eap.quickstart.logging.YOLOLogger as Log

class ARKGTrackViewModel(
    application: Application
) : TrackViewModel(application) {

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

    override suspend fun execute(client: FidoClient, activity: Activity) {
        clearLogs()

        this.client = client

        val domain = "demo.yubico.com"
        val domainName = "Yubico Demo"
        val challenge = Random.nextBytes(32)
        val sampleUserId: String = UUID.randomUUID().toString()

        val requestJson = createSignCredentialCreateOption(
            domain,
            domainName,
            challenge,
            sampleUserId
        )

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
            credential.derivePublicKeys(23)?.let { keys ->
                State.PublicKeysDerived(credential, keys)
            } ?: State.Error(
                title = "NoKeys",
                message = "Keys could not be generated.",
                logs = Log.logs
            )
        }
    }

    fun createSignatureWithKey(derivedKey: DerivedPublicKey) {
        if (state.value !is State.PublicKeysDerived) {
            return
        }

        val message = "Hello World"
        val messageSha256 = message.toByteArray().sha256()

        val domain = "demo.yubico.com"
        val challenge = Random.nextBytes(32)

        val assertionRequest = (state.value as State.PublicKeysDerived).credential.createSigningAttestationOption(
            message = messageSha256,
            domain = domain,
            challenge = challenge,
            derivedKey = derivedKey,
        ) ?: throw IllegalStateException("Could not create attestation request.")

        viewModelScope.launch {
            Log.i("CREDGET", "REQUEST_JSON")
            Log.i("CREDGET", assertionRequest)

            val result = client.getAssertion(
                origin = Origin("https://$domain"),
                request = assertionRequest,
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
