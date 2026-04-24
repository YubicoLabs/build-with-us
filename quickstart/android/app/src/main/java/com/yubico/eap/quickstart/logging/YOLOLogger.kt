package com.yubico.eap.quickstart.logging

import android.util.Log
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.android.LogcatAppender
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import com.yubico.yubikit.core.fido.CtapException

class YOLOLogger : LogcatAppender() {
    companion object {
        val logs: MutableList<String> = mutableListOf()

        fun append(event: ILoggingEvent?, logForAndroid: Boolean = false): Unit = synchronized(YOLOLogger) {
            if (event == null) {
                return
            }

            val addendum = when (val throwable = (event.throwableProxy as? ThrowableProxy)?.throwable) {
                is CtapException -> "\nCtapException (${throwable.humanErrorMessage})"
                is Throwable -> "\n$throwable"
                else -> ""
            }

            val message = "${event.formattedMessage}$addendum"
            logs.add("${event.level.levelStr}: $message")

            if (logForAndroid) {
                logOnAndroid(event)
            }
        }

        private fun logOnAndroid(event: ILoggingEvent) {
            when (event.level) {
                Level.OFF -> Log.v(
                    event.tag,
                    event.message,
                    (event.throwableProxy as? ThrowableProxy)?.throwable
                )

                Level.ERROR -> Log.e(
                    event.tag,
                    event.message,
                    (event.throwableProxy as? ThrowableProxy)?.throwable
                )

                Level.WARN -> Log.w(
                    event.tag,
                    event.message,
                    (event.throwableProxy as? ThrowableProxy)?.throwable
                )

                Level.INFO -> Log.i(
                    event.tag,
                    event.message,
                    (event.throwableProxy as? ThrowableProxy)?.throwable
                )

                Level.DEBUG -> Log.d(
                    event.tag,
                    event.message,
                    (event.throwableProxy as? ThrowableProxy)?.throwable
                )

                Level.TRACE -> Log.v(
                    event.tag,
                    event.message,
                    (event.throwableProxy as? ThrowableProxy)?.throwable
                )

                Level.ALL -> Log.d(
                    event.tag,
                    event.message,
                    (event.throwableProxy as? ThrowableProxy)?.throwable
                )
            }
        }

        fun i(tag: String, msg: String) {
            append(
                LoggingEvent().apply {
                    level = Level.INFO
                    message = "$tag: $msg"
                },
                true
            )
        }

        fun d(tag: String, msg: String) {
            append(
                LoggingEvent().apply {
                    level = Level.DEBUG
                    message = "$tag: $msg"
                },
                true
            )
        }

        fun e(tag: String, msg: String, th: Throwable? = null) {
            append(
                LoggingEvent().apply {
                    level = Level.ERROR
                    message = "$tag: $msg"
                    th?.let {
                        setThrowableProxy(ThrowableProxy(it))
                    }
                },
                true
            )
        }
    }

    override fun append(event: ILoggingEvent?) {
        super.append(event)
        YOLOLogger.append(
            event
        )
    }
}

private val ILoggingEvent.tag: String
    get() {
        val delimiterIndex = message.indexOf(":")
        return if (delimiterIndex in 0 until 10) {
            message.substring(0, delimiterIndex)
        } else {
            "INTERNAL"
        }
    }

// see https://fidoalliance.org/specs/fido-v2.1-ps-20210615/fido-client-to-authenticator-protocol-v2.1-ps-errata-20220621.html#error-responses
private val CtapException.humanErrorMessage: String
    get() = String.format("0x%02X - ", ctapError) + when (ctapError) {
        CtapException.ERR_SUCCESS -> "success: Indicates successful response."
        CtapException.ERR_INVALID_COMMAND -> "invalid command: The command is not a valid CTAP command."
        CtapException.ERR_INVALID_PARAMETER -> "invalid parameter: The command included an invalid parameter."
        CtapException.ERR_INVALID_LENGTH -> "invalid length: Invalid message or item length."
        CtapException.ERR_INVALID_SEQ -> "invalid seq: Invalid message sequencing."
        CtapException.ERR_TIMEOUT -> "timeout: Message timed out. "
        CtapException.ERR_CHANNEL_BUSY -> "channel busy: Client SHOULD retry the request after a short delay. Note that the client MAY abort the transaction if the command is no longer relevant."
        CtapException.ERR_LOCK_REQUIRED -> "lock required: Command requires channel lock."
        CtapException.ERR_INVALID_CHANNEL -> "invalid channel: Command not allowed on this cid."
        CtapException.ERR_CBOR_UNEXPECTED_TYPE -> "cbor unexpected type: Invalid/unexpected CBOR error."
        CtapException.ERR_INVALID_CBOR -> "invalid cbor: Error when parsing CBOR."
        CtapException.ERR_MISSING_PARAMETER -> "missing parameter: Missing non-optional parameter."
        CtapException.ERR_LIMIT_EXCEEDED -> "limit exceeded: Limit for number of items exceeded."
        CtapException.ERR_UNSUPPORTED_EXTENSION -> "unsupported extension"
        CtapException.ERR_FP_DATABASE_FULL -> "fp database full: Fingerprint data base is full, e.g., during enrollment."
        CtapException.ERR_LARGE_BLOB_STORAGE_FULL -> "large blob storage full: Large blob storage is full. (See § 6.10.3 Large, per-credential blobs.)"
        CtapException.ERR_CREDENTIAL_EXCLUDED -> "credential excluded: Valid credential found in the exclude list."
        CtapException.ERR_PROCESSING -> "processing: Processing (Lengthy operation is in progress)."
        CtapException.ERR_INVALID_CREDENTIAL -> "invalid credential: Credential not valid for the authenticator."
        CtapException.ERR_USER_ACTION_PENDING -> "user action pending: Authentication is waiting for user interaction."
        CtapException.ERR_OPERATION_PENDING -> "operation pending: Processing, lengthy operation is in progress."
        CtapException.ERR_NO_OPERATIONS -> "no operations: No request is pending."
        CtapException.ERR_UNSUPPORTED_ALGORITHM -> "unsupported algorithm: Authenticator does not support requested algorithm."
        CtapException.ERR_OPERATION_DENIED -> "operation denied: Not authorized for requested operation."
        CtapException.ERR_KEY_STORE_FULL -> "key store full: Internal key storage is full."
        CtapException.ERR_NOT_BUSY -> "not busy"
        CtapException.ERR_NO_OPERATION_PENDING -> "no operation pending"
        CtapException.ERR_UNSUPPORTED_OPTION -> "unsupported option: Unsupported option."
        CtapException.ERR_INVALID_OPTION -> "invalid option: Not a valid option for current operation."
        CtapException.ERR_KEEPALIVE_CANCEL -> "keepalive cancel: Pending keep alive was cancelled."
        CtapException.ERR_NO_CREDENTIALS -> "no credentials: No valid credentials provided."
        CtapException.ERR_USER_ACTION_TIMEOUT -> "user action timeout: A user action timeout occurred."
        CtapException.ERR_NOT_ALLOWED -> "not allowed: Continuation command, such as, authenticatorGetNextAssertion not allowed."
        CtapException.ERR_PIN_INVALID -> "pin invalid"
        CtapException.ERR_PIN_BLOCKED -> "pin blocked"
        CtapException.ERR_PIN_AUTH_INVALID -> "pin auth invalid: PIN authentication, pinUvAuthParam, verification failed."
        CtapException.ERR_PIN_AUTH_BLOCKED -> "pin auth blocked: PIN authentication using pinUvAuthToken blocked. Requires power cycle to reset."
        CtapException.ERR_PIN_NOT_SET -> "pin not set: No PIN has been set."
        CtapException.ERR_PUAT_REQUIRED -> "puat required: A pinUvAuthToken is required for the selected operation. See also the pinUvAuthToken option ID."
        CtapException.ERR_PIN_POLICY_VIOLATION -> "pin policy violation: PIN policy violation. Currently only enforces minimum length."
        CtapException.ERR_PIN_TOKEN_EXPIRED -> "pin token expired: Reserved for Future Use"
        CtapException.ERR_REQUEST_TOO_LARGE -> "request too large: Authenticator cannot handle this request due to memory constraints."
        CtapException.ERR_ACTION_TIMEOUT -> "action timeout: The current operation has timed out."
        CtapException.ERR_UP_REQUIRED -> "up required: User presence is required for the requested operation."
        CtapException.ERR_UV_BLOCKED -> "uv blocked: built-in user verification is disabled."
        CtapException.ERR_INTEGRITY_FAILURE -> "integrity failure: A checksum did not match."
        CtapException.ERR_INVALID_SUBCOMMAND -> "invalid subcommand: The requested subcommand is either invalid or not implemented."
        CtapException.ERR_UV_INVALID -> "uv invalid: built-in user verification unsuccessful. The platform SHOULD retry."
        CtapException.ERR_UNAUTHORIZED_PERMISSION -> "unauthorized permission: The permissions parameter contains an unauthorized permission."
        CtapException.ERR_OTHER -> "other: Other unspecified error."
        CtapException.ERR_SPEC_LAST -> "spec last: CTAP 2 spec last error."
        CtapException.ERR_EXTENSION_FIRST -> "extension first: Extension specific error."
        CtapException.ERR_EXTENSION_LAST -> "extension last: Extension specific error."
        CtapException.ERR_VENDOR_FIRST -> "vendor first: Vendor specific error."
        CtapException.ERR_VENDOR_LAST -> "vendor last: Vendor specific error."
        else -> "unknown error"
    }
