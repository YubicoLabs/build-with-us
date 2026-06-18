package com.yubico.eap.quickstart.track.credentials

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.yubico.eap.quickstart.helpers.encode64
import com.yubico.eap.quickstart.helpers.extractCredentialId
import com.yubico.eap.quickstart.track.TrackViewModel
import com.yubico.yubikit.fido.android.ui.FidoClient
import com.yubico.yubikit.fido.android.ui.Origin
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID
import kotlin.random.asKotlinRandom
import com.yubico.eap.quickstart.logging.YOLOLogger as Log

class CredentialTrackViewModel(
    application: Application
) : TrackViewModel(application) {

    data class CreatedAndAttested(
        val createCredentialOption: String,
        val createCredentialResponse: String,
        val assertCredentialOption: String,
        val assertCredentialResponse: String,
    )

    sealed class State {
        data class CredentialCreatedAndAsserted(
            val createCredentialOption: String,
            val createCredentialResponse: String,
            val assertCredentialOption: String,
            val assertCredentialResponse: String,
        ) : State()

        data class Error(
            val title: String,
            val message: String,
            val logs: List<String>
        ) : State()

        object InProgress : State()
    }

    val state: MutableState<State> = mutableStateOf(State.InProgress)

    override suspend fun execute(client: FidoClient) {
        viewModelScope.launch {
            clearLogs()
            state.value = State.InProgress

            val domain = "demo.yubico.com"
            val domainName = "Yubico Demo"

            val (createOption, createResponse, attestOption, attestResponse) = createAndAttestCredential(
                client,
                domain,
                domainName
            ) ?: throw IllegalStateException("Could not create credential.")

            state.value = State.CredentialCreatedAndAsserted(
                createCredentialOption = createOption,
                createCredentialResponse = createResponse,
                assertCredentialOption = attestOption,
                assertCredentialResponse = attestResponse,
            )
        }
    }


    fun clearLogs() {
        Log.logs.clear()

        (state.value as? State.Error)?.let { typedState ->
            state.value = typedState.copy(logs = Log.logs)
        }
    }

    private suspend fun createAndAttestCredential(
        client: FidoClient,
        domain: String,
        domainName: String
    ): CreatedAndAttested? {
        val challenge = SecureRandom.getInstanceStrong().asKotlinRandom().nextBytes(32)

        val credentialCreateOptions = """
        {
            "challenge": "${challenge.encode64()}",
            "rp": {
                "id": "$domain", 
                "name": "$domainName"
            },
            "user": {
                "id": "${UUID.randomUUID()}",
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
            "attestation": "none"
        }
        """.trimIndent()

        val credentialResultOption = client.makeCredential(
            origin = Origin("https://$domain"),
            request = credentialCreateOptions,
            clientDataHash = null, // let the SDK calculate
        )

        return if (!credentialResultOption.isSuccess) {
            null
        } else {
            val credentialResult = credentialResultOption.getOrThrow()
            val (credentialAttestationOptions, credentialAssertionResult) = attestCredential(
                client, domain, credentialResult
            )

            CreatedAndAttested(
                createCredentialOption = credentialCreateOptions,
                createCredentialResponse = credentialResult,
                assertCredentialOption = credentialAttestationOptions,
                assertCredentialResponse = (credentialAssertionResult.getOrNull() ?: "<ATTESTATION ERROR>")
            )
        }
    }

    private suspend fun attestCredential(
        client: FidoClient,
        domain: String,
        createdCredential: String,
    ): Pair<String, Result<String>> {
        val challenge = SecureRandom.getInstanceStrong().asKotlinRandom().nextBytes(32)
        val credentialId = JSONObject(createdCredential).extractCredentialId()

        val credentialAttestationOptions = """ {
            "challenge": "${challenge.encode64()}",
            "rpId": "$domain",
            "allowCredentials" : [{
                "type": "public-key",
                "id": "$credentialId"
            }],
            "userVerification": "discouraged"
        }
        """.trimIndent()


        return credentialAttestationOptions to client.getAssertion(
            origin = Origin("https://$domain"),
            request = credentialAttestationOptions,
            clientDataHash = null, // let the SDK calculate
        )
    }
}
