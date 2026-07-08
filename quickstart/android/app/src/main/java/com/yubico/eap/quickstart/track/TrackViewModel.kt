package com.yubico.eap.quickstart.track

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.yubico.yubikit.fido.android.ui.FidoClient

abstract class TrackViewModel(
    application: Application
) : AndroidViewModel(application) {
    abstract suspend fun execute(client: FidoClient, activity: Activity)
}
