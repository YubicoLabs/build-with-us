package com.yubico.eap.quickstart.track.signing

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.yubico.eap.quickstart.helpers.DerivedPublicKey
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
import java.security.SecureRandom
import java.util.UUID
import kotlin.random.asKotlinRandom
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

    override suspend fun execute(client: FidoClient, activity: Activity) {
        viewModelScope.launch {
            clearLogs()
            state.value = State.InProgress

            val domain = "demo.yubico.com"
            val domainName = "Yubico Demo"

            val credential = createCredential(client, domain, domainName)
                ?: throw IllegalStateException("Could not create credential.")

            val publicKey = credential.derivePublicKeys(keyCount = 1)?.first()
                ?: throw IllegalStateException("Couldn't generate public key.")

            val message = loadFrankestein()
            val messageSha256 = message.toByteArray().sha256()

            state.value = attestCredentialAndSign(
                client,
                publicKey,
                message,
                messageSha256,
                domain,
                credential,
            ) ?: State.Error("NO VER", "Could not verify.", Log.logs)
        }
    }


    fun clearLogs() {
        Log.logs.clear()

        (state.value as? State.Error)?.let { typedState ->
            state.value = typedState.copy(logs = Log.logs)
        }
    }

    private suspend fun createCredential(
        client: FidoClient,
        domain: String,
        domainName: String
    ): JSONObject? {
        val challenge = SecureRandom.getInstanceStrong().asKotlinRandom().nextBytes(32)

        val requestJson = createSignCredentialCreateOption(
            domain = domain,
            domainName = domainName,
            challenge = challenge,
            userId = UUID.randomUUID().toString(),
        )

        val credentialResult = client.makeCredential(
            origin = Origin("https://$domain"),
            request = requestJson,
            clientDataHash = null, // let the SDK calculate
        )

        return if (!credentialResult.isSuccess) {
            null
        } else {
            JSONObject(credentialResult.getOrThrow())
        }
    }

    private suspend fun attestCredentialAndSign(
        client: FidoClient,
        publicKey: DerivedPublicKey,
        message: String,
        messageSha256: ByteArray,
        domain: String,
        credential: JSONObject,
    ): State.SignedAndVerified? {
        val challenge = SecureRandom.getInstanceStrong().asKotlinRandom().nextBytes(32)

        val attestationRequest = credential.createSigningAttestationOption(
            messageSha256,
            domain,
            challenge,
            publicKey
        ) ?: throw IllegalStateException("Could not create attestation request.")

        Log.i("CREDGET", "REQUEST_JSON")
        Log.i("CREDGET", attestationRequest)

        val result = client.getAssertion(
            origin = Origin("https://$domain"),
            request = attestationRequest,
            clientDataHash = null,
        )

        Log.i("RES", result.toString())

        return JSONObject(
            result.getOrThrow()
        ).extractSignature()
            ?.let { signature ->
                val verified = verifySignature(
                    publicKey.publicKey,
                    messageSha256,
                    signature
                )

                State.SignedAndVerified(
                    message = message,
                    messageHash = messageSha256,
                    signature = signature,
                    verified = verified,
                )
            }
    }

    private fun loadFrankestein(): String = String(
        getApplication<Application>().assets.open("frankenstein.txt").readBytes()
    )
}
