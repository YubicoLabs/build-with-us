package com.yubico.eap.quickstart.helpers

import com.yubico.yubikit.core.internal.codec.Base64.fromUrlSafeString
import com.yubico.yubikit.core.internal.codec.Base64.toUrlSafeString
import org.bouncycastle.jcajce.provider.digest.SHA256

fun Any?.pretty(): String = when (this) {
    is Map<*, *> -> "{${
        entries.joinToString(", ") { (k, v) ->
            "$k: ${v.pretty()}"
        }
    }}"

    is Iterable<*> -> "[${
        joinToString(", ") {
            it.pretty()
        }
    }]"

    is ByteArray -> toHexString()

    else -> toString()
}

fun String.ellipsize(maxCharacters: Int): String = when {
    codePointCount(0, length) <= maxCharacters -> this

    else -> "${substring(0, offsetByCodePoints(0, maxCharacters - 1))}…"
}

//fun String.decode64(): ByteArray = Base64.decode(this, Base64.URL_SAFE or Base64.NO_WRAP)
fun String.decode64(): ByteArray = fromUrlSafeString(this)

//fun ByteArray.encode64(): String = String(Base64.encode(this, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))

fun ByteArray.encode64(): String = toUrlSafeString(this)


fun ByteArray.sha256(): ByteArray = SHA256.Digest().digest(this)

operator fun String.times(n: Int): String = (0..<n).joinToString("") { this }
