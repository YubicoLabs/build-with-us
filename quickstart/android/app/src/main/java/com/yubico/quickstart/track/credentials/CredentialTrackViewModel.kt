package com.yubico.quickstart.track.credentials

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.yubico.quickstart.helpers.CredentialContainer
import com.yubico.quickstart.helpers.DOMAIN
import com.yubico.quickstart.helpers.DOMAIN_NAME
import com.yubico.quickstart.helpers.encode64
import com.yubico.quickstart.helpers.extractCredentialId
import com.yubico.quickstart.helpers.findAlgorithm
import com.yubico.quickstart.track.TrackViewModel
import com.yubico.yubikit.fido.android.ui.FidoClient
import com.yubico.yubikit.fido.android.ui.Origin
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID
import kotlin.random.asKotlinRandom
import com.yubico.quickstart.logging.YOLOLogger as Log

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
        data class AlgorithmsFound(
            val algorithms: Map<Int, String>
        ) : State()

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

    lateinit var client: FidoClient

    override suspend fun execute(client: FidoClient, activity: Activity) {
        this.client = client
        viewModelScope.launch {
            clearLogs()
            state.value = State.InProgress

            CredentialContainer(activity).getInfo(
                failureCallback = {
                    state.value = State.Error("ERRROR", "$it", Log.logs)
                }
            ) { info ->
                state.value = State.AlgorithmsFound(
                    (info.session.algorithms as List<*>).map { algorithm ->
                        val alg = (algorithm as Map<*, *>).getOrDefault("alg", null).toString().toIntOrNull() ?: 0
                        alg to (findAlgorithm(alg)?.description ?: "Unknown")
                    }.toMap()
                )
            }
        }
    }

    fun createCredential(algId: Int) {
        state.value = State.InProgress

        viewModelScope.launch {
            val (createOption, createResponse, attestOption, attestResponse) = createAndAttestCredential(
                algorithmId = algId,
                domain = DOMAIN,
                domainName = DOMAIN_NAME
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
        algorithmId: Int,
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
                "name": "bwu@yubico.com",
                "displayName": "Build With Us"
            },
            "pubKeyCredParams": [
                {
                    "type": "public-key",
                    "alg": $algorithmId
                }
            ],
            "authenticatorSelection": {
              "authenticatorAttachment": "cross-platform",
              "residentKey": "required",
              "userVerification": "discouraged",
              "requireResidentKey": true
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
