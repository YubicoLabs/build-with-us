package com.yubico.eap.quickstart

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
            extensions = listOf()
        )
    }

    fun startTrack(trackIndex: Int) {
        when (trackIndex) {
            1 -> {
                val newTrackVM = ARKGTrackViewModel(application)
                trackVm.value = newTrackVM
                trackNumber.value = 1

                viewModelScope.launch {
                    newTrackVM.execute(fido)
                }
            }

            else -> TODO("Implement track with index $trackIndex.")
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
