package com.yubico.eap.quickstart

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.yubico.eap.quickstart.track.arkg.ARKGTrackViewModel
import com.yubico.eap.quickstart.track.credentials.CredentialTrackViewModel
import com.yubico.eap.quickstart.track.info.InfoTrackViewModel
import com.yubico.eap.quickstart.track.signing.SigningTrackViewModel
import com.yubico.yubikit.fido.android.ui.FidoClient
import kotlinx.coroutines.launch

class QuickstartVM(
    application: Application
) : AndroidViewModel(application) {

    val trackNumber: MutableState<Int?> = mutableStateOf(null)

    val trackVm: MutableState<ViewModel?> = mutableStateOf(null)

    private lateinit var fido: FidoClient

    fun setup(activity: ComponentActivity) {
        fido = FidoClient(
            activity = activity,
        )
    }

    fun startTrack(trackIndex: Int, activity: Activity) {
        val newTrackVM = when (trackIndex) {
            0 -> InfoTrackViewModel(application)
            1 -> CredentialTrackViewModel(application)
            2 -> SigningTrackViewModel(application)
            3 -> ARKGTrackViewModel(application)

            else -> TODO("Implement track with index $trackIndex.")
        }

        trackVm.value = newTrackVM
        trackNumber.value = trackIndex

        viewModelScope.launch {
            newTrackVM.execute(fido, activity)
        }

    }

    fun copyToClipBoard(message: String) {
        application
            .getSystemService(
                /*serviceClass =*/ ClipboardManager::class.java
            )
            .setPrimaryClip(
                ClipData.newPlainText(
                    /*label =*/ "Quickstart",
                    /*text =*/ message
                )
            )
    }
}
