package com.yubico.eap.quickstart.track.info

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.security.keystore.UserNotAuthenticatedException
import android.text.InputType
import android.util.Base64.NO_PADDING
import android.util.Base64.NO_WRAP
import android.util.Base64.URL_SAFE
import android.util.Base64.encodeToString
import android.widget.EditText
import com.yubico.eap.quickstart.track.info.Operation.CreateOperation
import com.yubico.eap.quickstart.track.info.Operation.GetInfoOperation
import com.yubico.eap.quickstart.track.info.Operation.GetOperation
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcNotAvailable
import com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice
import com.yubico.yubikit.android.transport.usb.UsbConfiguration
import com.yubico.yubikit.android.transport.usb.UsbYubiKeyDevice
import com.yubico.yubikit.core.YubiKeyConnection
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.fido.CtapException
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import com.yubico.yubikit.core.util.Callback
import com.yubico.yubikit.fido.client.MultipleAssertionsAvailable
import com.yubico.yubikit.fido.client.WebAuthnClient
import com.yubico.yubikit.fido.client.clientdata.ClientDataProvider
import com.yubico.yubikit.fido.ctap.Ctap2Session
import com.yubico.yubikit.fido.webauthn.PublicKeyCredential
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialCreationOptions
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialDescriptor
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialRequestOptions
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import com.yubico.eap.quickstart.logging.YOLOLogger.Companion as Log

sealed class Operation(
    open val failure: (Throwable) -> Unit,
) {

    data class GetInfoOperation(
        val success: (Ctap2Session.InfoData) -> Unit,
        override val failure: (Throwable) -> Unit,
    ) : Operation(failure)

    data class CreateOperation(
        val options: PublicKeyCredentialCreationOptions,
        val success: (PublicKeyCredential) -> Unit,
        override val failure: (Throwable) -> Unit,
    ) : Operation(failure)

    data class GetOperation(
        val options: PublicKeyCredentialRequestOptions,
        val success: (credential: PublicKeyCredential) -> Unit,
        override val failure: (Throwable) -> Unit,
    ) : Operation(failure)
}

private data class LastPin(
    val pin: String,
    val timestamp: ValueTimeMark,
)

private const val tagForLog = "Credentials"

class CredentialContainer(
    val activity: Activity,
) {
    private val manager: YubiKitManager = YubiKitManager(activity)

    private val usbListener: Callback<UsbYubiKeyDevice> =
        Callback { device ->
            deviceConnected(device)
        }

    private val nfcListener: Callback<NfcYubiKeyDevice> =
        Callback { device ->
            deviceConnected(device)
        }

    private fun startDiscoveries() {
        manager.startUsbDiscovery(
            UsbConfiguration().handlePermissions(true),
            usbListener,
        )

        try {
            manager.startNfcDiscovery(
                NfcConfiguration().timeout(60_000),
                activity,
                nfcListener,
            )
        } catch (e: NfcNotAvailable) {
            Log.e(tagForLog, "No NFC, ignoring.", e)
        }
    }

    private var lastOperation: Operation? = null

    private var lastPinUsed: LastPin? = null

    fun create(
        options: PublicKeyCredentialCreationOptions,
        successCallback: (PublicKeyCredential) -> Unit,
        failureCallback: (Throwable) -> Unit,
    ) {
        Log.i(tagForLog, "yubico create implementation called.")

        startDiscoveries()

        lastOperation =
            CreateOperation(
                options = options,
                success = successCallback,
                failure = {
                    lastPinUsed = null
                    failureCallback(it)
                }
            )
    }

    fun getInfo(
        failureCallback: (Throwable) -> Unit = { Log.e(tagForLog, "NO INFO", it) },
        successCallback: (Ctap2Session.InfoData) -> Unit,
    ) {
        Log.i(tagForLog, "yubico getinfo implementation called.")
        startDiscoveries()

        lastOperation =
            GetInfoOperation(
                success = successCallback,
                failure = {
                    lastPinUsed = null
                    failureCallback(it)
                }
            )
    }

    fun get(
        options: PublicKeyCredentialRequestOptions,
        successCallback: (credential: PublicKeyCredential) -> Unit,
        failureCallback: (Throwable) -> Unit,
    ) {
        Log.i(tagForLog, "yubico get implementation called.")

        startDiscoveries()

        lastOperation =
            GetOperation(
                options = options,
                success = successCallback,
                failure = {
                    lastPinUsed = null
                    failureCallback(it)
                },
            )
    }

    private fun askForPin(
        operation: Operation,
        device: YubiKeyDevice,
    ) {
        requestPin { providedPin ->
            if (providedPin != null) {
                routeToCorrectMethodWithPin(operation, device, providedPin)
            } else {
                operation.failure(
                    UserNotAuthenticatedException(
                        "User did enter empty pin.",
                    ),
                )
            }
        }
    }

    private fun routeToCorrectMethodWithPin(
        operation: Operation,
        device: YubiKeyDevice,
        pin: String?,
    ) {
        try {
            when (operation) {
                is CreateOperation ->
                    createWithDevice(
                        device,
                        operation,
                        pin,
                    )

                is GetOperation ->
                    getWithDevice(
                        device,
                        operation,
                        pin,
                    )

                is GetInfoOperation ->
                    getInfoWithDevice(
                        device,
                        operation
                    )
            }
        } catch (e: Throwable) {
            Log.e(tagForLog, "Something went wrong.", e)
        }
    }

    private fun deviceConnected(device: YubiKeyDevice) {
        lastOperation?.let { operation ->
            if (operation is GetInfoOperation) {
                routeToCorrectMethodWithPin(operation, device, null)
            } else {
                askForPin(operation, device)
            }
        }
    }

    private fun createWithDevice(
        device: YubiKeyDevice,
        operation: CreateOperation,
        pin: String?,
    ) {
        val connection = device.openConnection(SmartCardConnection::class.java)
        createWithConnection(
            connection,
            operation,
            pin,
        )
    }

    private fun createWithConnection(
        connection: YubiKeyConnection,
        operation: CreateOperation,
        pin: String?,
    ) {
        val client = WebAuthnClient.create(connection, listOf(), null)
        val kitOptions = operation.options
        val domain = kitOptions.rp.id ?: ""

        val clientJson: ByteArray =
            getClientOptions(
                type = "webauthn.create",
                origin = domain,
                challenge =
                    encodeToString(
                        kitOptions.challenge,
                        NO_PADDING or NO_WRAP or URL_SAFE,
                    ),
            )

        val enterprise = null
        val state = null

        try {
            val result: PublicKeyCredential =
                client.makeCredential(
                    ClientDataProvider.fromClientDataJson(clientJson),
                    kitOptions,
                    domain,
                    pin?.toCharArray(),
                    enterprise,
                    state,
                )
            client.close()

            Log.i(tagForLog, "Done, created $result.")
            operation.success(result)
        } catch (ctap: CtapException) {
            Log.e(tagForLog, "Protocol exception: '${ctap.ctapError.toHumanReadable()}'.", ctap)
            operation.failure(ctap)
        } catch (th: Throwable) {
            Log.e(tagForLog, "Unexpected error: '${th.message}'.", th)
            operation.failure(th)
        } finally {
            lastOperation = null
            connection.close()
        }
    }

    private fun getWithDevice(
        device: YubiKeyDevice,
        operation: GetOperation,
        pin: String?,
    ) {
        val connection = device.openConnection(SmartCardConnection::class.java)
        getWithSession(
            connection,
            operation,
            pin,
        )
    }

    private fun getInfoWithDevice(
        device: YubiKeyDevice,
        operation: GetInfoOperation,
    ) {
        val connection = device.openConnection(SmartCardConnection::class.java)
        val session = Ctap2Session(connection)
        val info = session.info
        session.close()

        operation.success(info)
    }

    private fun getWithSession(
        connection: YubiKeyConnection,
        operation: GetOperation,
        pin: String?,
    ) {
        val client = WebAuthnClient.create(connection, listOf(), null)

        val kitOptions = operation.options
        val domain = kitOptions.rpId ?: ""
        val clientDataJson =
            getClientOptions(
                type = "webauthn.get",
                origin = domain,
                challenge =
                    encodeToString(
                        kitOptions.challenge,
                        NO_PADDING or NO_WRAP or URL_SAFE,
                    ),
            )
        val enterprise = null
        try {
            val result =
                client.getAssertion(
                    ClientDataProvider.fromClientDataJson(clientDataJson),
                    kitOptions,
                    domain,
                    pin?.toCharArray(),
                    enterprise,
                )

            client.close()

            Log.i(tagForLog, "Done, got $result.")
            operation.success(result)
        } catch (ctap: CtapException) {
            Log.e(tagForLog, "Protocol exception: '${ctap.ctapError.toHumanReadable()}'.", ctap)
            operation.failure(ctap)
        } catch (multiple: MultipleAssertionsAvailable) {
            Log.i(tagForLog, "Found several assertions. User selection needed.")
            requestSelection(multiple, kitOptions, operation)
        } catch (th: Throwable) {
            Log.e(tagForLog, "Unexpected error: '${th.message}'.", th)
            operation.failure(th)
        } finally {
            lastOperation = null
            connection.close()
        }
    }

    private fun requestSelection(
        multiple: MultipleAssertionsAvailable,
        kitOptions: PublicKeyCredentialRequestOptions,
        operation: GetOperation,
    ) {
        askForCredentialSelection(
            multiple,
            success = { credential ->
                get(
                    PublicKeyCredentialRequestOptions(
                        kitOptions.challenge,
                        kitOptions.timeout,
                        kitOptions.rpId,
                        mutableListOf(
                            PublicKeyCredentialDescriptor(
                                "public-key",
                                credential.rawId,
                                null,
                            ),
                        ),
                        kitOptions.userVerification,
                        kitOptions.extensions
                    ),
                    operation.success,
                    operation.failure,
                )
            },
            failure = {
                operation.failure(Throwable("Credential not selected."))
            },
        )
    }

    private fun askForCredentialSelection(
        available: MultipleAssertionsAvailable,
        success: (PublicKeyCredential) -> Unit,
        failure: () -> Unit,
    ) {
        Dispatchers.Main.dispatch(EmptyCoroutineContext) {
            val items = available.users.map { it.displayName }.toTypedArray()
            val listener =
                DialogInterface.OnClickListener { dialog, which ->
                    val credential = available.select(which)
                    Log.i(tagForLog, "credential selected: $credential")
                    dialog?.dismiss()

                    success(credential)
                }

            AlertDialog.Builder(activity)
                .setTitle("select credential")
                .setItems(items, listener)
                .setNegativeButton(android.R.string.cancel) { dialog, which ->
                    Log.i(tagForLog, "No user selected.")
                    dialog.dismiss()
                    failure()
                }
                .show()
        }
    }

    private fun requestPin(callback: (String?) -> Unit) {
        if (lastPinUsed != null &&
            (lastPinUsed!!.timestamp + 60.seconds).hasNotPassedNow()
        ) {
            lastPinUsed = lastPinUsed!!.copy(timestamp = TimeSource.Monotonic.markNow())
            callback(lastPinUsed?.pin)
            return
        }

        Dispatchers.Main.dispatch(EmptyCoroutineContext) {

            val pinEdit =
                EditText(activity).apply {
                    hint = "Enter your PIN"
                    maxLines = 1
                    minLines = 1
                    inputType =
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }

            val dialog =
                AlertDialog.Builder(activity)
                    .setTitle("Please enter your PIN.")
                    .setView(pinEdit)
                    .setPositiveButton(android.R.string.ok) { dialog, which ->
                        Log.i(tagForLog, "PIN entered.")
                        dialog.dismiss()
                        lastPinUsed = LastPin(
                            pin = pinEdit.text.toString(),
                            timestamp = TimeSource.Monotonic.markNow()
                        )
                        callback(pinEdit.text.toString())
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, which ->
                        Log.i(tagForLog, "PIN entry cancelled.")
                        dialog.dismiss()
                        callback(null)
                    }.show()

            pinEdit.setOnEditorActionListener { v, actionId, event ->
                dialog.dismiss()
                callback(v.text.toString())
                true
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun Byte.toHumanReadable(): String =
    CtapException::class.java.declaredFields.filter {
        it.name.startsWith("ERR_")
    }.firstOrNull { constant ->
        (constant.get(null) as Byte) == this
    }?.name
        ?: "Unknown CTAP ERROR"


private fun getClientOptions(
    type: String,
    challenge: String,
    origin: String,
) = "{\"type\":\"$type\",\"challenge\":\"$challenge\",\"origin\":\"https://$origin\"}"
    .toByteArray()
