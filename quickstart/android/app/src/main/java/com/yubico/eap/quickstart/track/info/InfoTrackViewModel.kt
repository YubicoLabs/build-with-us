package com.yubico.eap.quickstart.track.info

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.yubico.eap.quickstart.helpers.CredentialContainer
import com.yubico.eap.quickstart.helpers.Operation
import com.yubico.eap.quickstart.track.TrackViewModel
import com.yubico.yubikit.fido.android.ui.FidoClient
import com.yubico.yubikit.fido.ctap.Ctap2Session
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

    sealed class State {
        data class InformationRequested(
            val information: Operation.GetInfoOperation.Information
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
                    information = info,
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
