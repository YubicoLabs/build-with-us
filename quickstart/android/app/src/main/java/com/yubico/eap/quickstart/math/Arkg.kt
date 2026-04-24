package com.yubico.eap.quickstart.math

import com.yubico.eap.quickstart.helpers.sha256
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.nist.NISTNamedCurves
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.nio.ByteBuffer
import kotlin.experimental.xor
import kotlin.math.ceil


object Arkg {
    val CurveParams: X9ECParameters = NISTNamedCurves.getByName("P-256")
    val Domain = ECDomainParameters(CurveParams.curve, CurveParams.g, CurveParams.n, CurveParams.h)
    val DstExtBytes: ByteArray = "ARKG-P256".toByteArray()
    val KemDstExtBytes: ByteArray = "ARKG-ECDH.ARKG-P256".toByteArray()
    val HashToFieldL = 48

    fun deriveArkgPublicKey(
        pkKem: ECPoint,
        pkBl: ECPoint,
        ikm: ByteArray,
        context: ByteArray
    ): Pair<ByteArray, ByteArray> {
        if (context.size > 64) {
            throw IllegalStateException("ctx must be at most 64 bytes")
        }

        val ctxPrime = byteArrayOf(context.size.toByte()) + context
        val ctxBl = "ARKG-Derive-Key-BL.".toByteArray() + ctxPrime
        val ctxKem = "ARKG-Derive-Key-KEM.".toByteArray() + ctxPrime

        val (ikmTau, c) = kemEncaps(pkKem, ikm, ctxKem)

        val tau = blPrf(ikmTau, ctxBl)

        val tauG = Domain.g.multiply(tau).normalize()
        val pkPrime = pkBl.add(tauG).normalize()

        return pkPrime.getEncoded(false) to c
    }

    fun verifySignature(
        publicKey: ByteArray,
        message: ByteArray,
        signature: ByteArray
    ): Boolean {
        val point = CurveParams.curve.decodePoint(publicKey)
        val public = ECPublicKeyParameters(point, Domain)

        val signer = ECDSASigner()
        signer.init(false, public)

        return ASN1InputStream(signature).use { asn1 ->
            val sequence = asn1.readObject() as ASN1Sequence
            val r = ASN1Integer.getInstance(sequence.getObjectAt(0)).value
            val s = ASN1Integer.getInstance(sequence.getObjectAt(1)).value
            signer.verifySignature(message, r, s)
        }
    }

    private fun kemEncaps(pkKem: ECPoint, ikm: ByteArray, ctxKem: ByteArray): Pair<ByteArray, ByteArray> {
        val ctxSub = "ARKG-KEM-HMAC.".toByteArray() + ctxKem

        val (kPrime, cPrime) = subKemEncaps(pkKem, ikm, ctxSub)

        val mk = hkdfSha256(
            kPrime,
            "ARKG-KEM-HMAC-mac.".toByteArray() + KemDstExtBytes + ctxKem,
            32
        )

        val fullMac = generateHmac(mk, cPrime)

        val tau = fullMac.copyOfRange(0, 16)

        val k = hkdfSha256(
            kPrime,
            "ARKG-KEM-HMAC-shared.".toByteArray() + KemDstExtBytes + ctxKem,
            kPrime.size
        )

        return k to tau + cPrime
    }

    private fun hkdfSha256(ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val generator = HKDFBytesGenerator(SHA256Digest())
        generator.init(
            HKDFParameters(
                ikm,
                null,
                info
            )
        )

        val target = ByteArray(length)
        generator.generateBytes(target, 0, length)
        return target
    }

    private fun generateHmac(derivedKey: ByteArray, message: ByteArray): ByteArray {
        val hmac: HMac = HMac(SHA256Digest())
        hmac.init(KeyParameter(derivedKey))
        hmac.update(message, 0, message.size)

        val out = ByteArray(hmac.macSize)
        hmac.doFinal(out, 0)
        return out
    }

    private fun subKemEncaps(pkKem: ECPoint, ikm: ByteArray, ctx: ByteArray): Pair<ByteArray, ByteArray> {
        val (pkPrime, skPrime) = subKemDeriveKeyPair(ikm)

        val k = ecdh(pkKem, skPrime)

        return k to pkPrime.getEncoded(false)
    }

    private fun subKemDeriveKeyPair(ikm: ByteArray): Pair<ECPoint, BigInteger> {
        val dst = "ARKG-KEM-ECDH-KG.".toByteArray() + KemDstExtBytes

        val sk = hashToField(ikm, 1, dst)[0]

        val pk = Domain.g.multiply(sk).normalize()
        return pk to sk
    }

    private fun blPrf(ikmTau: ByteArray, ctxBl: ByteArray): BigInteger {
        val dstTau = "ARKG-BL-EC.".toByteArray() + DstExtBytes + ctxBl
        return hashToField(ikmTau, 1, dstTau)[0]
    }

    private fun hashToField(msg: ByteArray, count: Int, dst: ByteArray): List<BigInteger> {
        val expandMessageXmd = expandMessageXmd(msg, count * HashToFieldL, dst)
        val uniformBytes = ByteBuffer.wrap(expandMessageXmd)
        val elements = List(count) { i ->
            val offset = HashToFieldL * i
            val tv = ByteArray(HashToFieldL)
            uniformBytes.get(tv, offset, HashToFieldL)

            BigInteger(1, tv).remainder(Domain.n)
        }

        return elements
    }

    private fun ecdh(publicKey: ECPoint, privateKey: BigInteger): ByteArray {
        val sharedPoint = publicKey.multiply(privateKey).normalize()

        return sharedPoint.affineXCoord.encoded
    }

    private fun expandMessageXmd(msg: ByteArray, lenInBytes: Int, dst: ByteArray): ByteArray {
        val sInBytes = 64
        val bInBytes = 32

        val ell = ceil(lenInBytes / bInBytes.toFloat()).toInt()
        if (ell > 255 || lenInBytes > 65535 || dst.size > 255) {
            throw IllegalStateException("Invalid size of input/output")
        }

        val dstPrime = dst + dst.size.toByte()
        val zPad = ByteArray(sInBytes)
        val lIBStr = byteArrayOf((lenInBytes shr 8).toByte(), (lenInBytes and 0xFF).toByte())
        val msgPrime = zPad + msg + lIBStr + byteArrayOf(0x00) + dstPrime

        val b0 = msgPrime.sha256()
        var bXor = b0.copyOf()
        val uniformBytes = ByteBuffer.allocate(ell * bInBytes)

        for (i in 1..ell) {
            val input = bXor + byteArrayOf(i.toByte()) + dstPrime
            val bi = input.sha256()
            uniformBytes.put(bi, 0, bInBytes)

            bXor = b0 xor bi
        }

        val result = uniformBytes.array().take(lenInBytes).toByteArray()
        return result
    }

    private infix fun ByteArray.xor(other: ByteArray): ByteArray = indices.map {
        this[it] xor other[it]
    }.toByteArray()
}
