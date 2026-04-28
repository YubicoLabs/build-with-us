package com.yubico.eap.quickstart.helpers

import com.yubico.eap.quickstart.math.Arkg
import com.yubico.yubikit.fido.Cbor
import org.bouncycastle.math.ec.ECPoint
import org.json.JSONObject
import java.math.BigInteger
import com.yubico.eap.quickstart.logging.YOLOLogger as Log

const val SignExtensionName = "previewSign"

data class SigningResponse(
    val algorithm: Int,
    val type: Int,
    val blindingPublicKey: PublicKey,
    val blindingPoint: ECPoint,
    val encapsulatingPublicKey: PublicKey,
    val encapsulationPoint: ECPoint,
)

data class PublicKey(
    val curveType: Int,
    val keyType: Int,
    val derivationAlgorithm: Int,
    val x: BigInteger,
    val y: BigInteger,
)

data class DerivedPublicKey(
    val context: ByteArray,
    val publicKey: ByteArray,
    val keyHandle: ByteArray,
)

fun JSONObject.extractSigningResponse(): SigningResponse =
    optNestedString(
        "clientExtensionResults.$SignExtensionName.generatedKey.publicKey"
    )?.let { blindingPublicKeyB64 ->
        val blindingPublicKeyCbor = blindingPublicKeyB64.decode64()
        val blindingPublicKeyMap = Cbor.decode(blindingPublicKeyCbor) as Map<Int, *>
        val publicKey = blindingPublicKeyMap.coseToPublicKey()

        publicKey
    } ?: throw IllegalStateException("Signing Response not found in ${toString(0)}")

fun JSONObject.extractCredentialId(): String = getString("id")

fun JSONObject.extractKeyHandle(): String? =
    optNestedString(
        "clientExtensionResults.previewSign.generatedKey.keyHandle"
    )

fun JSONObject.extractPublicKey(): ByteArray? =
    optNestedString(
        "yolo.you.got.to.replace.this"
    )?.decode64()

fun JSONObject.extractSignature(): ByteArray? {
    return optNestedString(
        "clientExtensionResults.previewSign.signature"
    )?.decode64()
}

fun Map<Int, *>.coseToPublicKey(): SigningResponse = try {
    Log.i("SignCose", "Cose: ${pretty()}")

    val type = get(1) as Int
    val deriveKeyAlgorithm = get(-3) as Int
    val algorithm = get(3)

    if (type != -65537) {
        throw IllegalArgumentException("Key type of $type is not supported for ARKG.")
    } else if (algorithm != -65700) {
        throw IllegalArgumentException("Algorithm type of $algorithm is not supported.")
    } else if (deriveKeyAlgorithm != -9) {
        throw IllegalArgumentException("Key derivation algorithm type of $deriveKeyAlgorithm is not supported.")
    } else {
        // TODO: CHECK FOR NON ARKG algorithm / types/ KEMs
        val blinding = (get(-1) as Map<*, *>).asPublicKey()
        val encapsulation = (get(-2) as Map<*, *>).asPublicKey()

        val blindingPoint = Arkg.CurveParams.curve.createPoint(
            blinding.x,
            blinding.y
        )

        val kemPoint = Arkg.CurveParams.curve.createPoint(
            encapsulation.x,
            encapsulation.y,
        )

        SigningResponse(
            algorithm = algorithm as Int,
            type = type,
            blindingPublicKey = blinding,
            blindingPoint = blindingPoint,
            encapsulatingPublicKey = encapsulation,
            encapsulationPoint = kemPoint,
        ).also {
            Log.i("SignPar", "$it")
        }
    }
} catch (th: Throwable) {
    Log.e("COSE", "Not COSE-parsable: ${this.pretty()}", th)
    throw th
}

fun Map<*, *>.asPublicKey() = PublicKey(
    curveType = get(-1) as Int,
    keyType = get(1) as Int,
    derivationAlgorithm = get(3) as Int,
    x = BigInteger(1, get(-2) as ByteArray),
    y = BigInteger(1, get(-3) as ByteArray),
)
