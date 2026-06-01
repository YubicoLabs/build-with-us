// ARKG.swift
// Swift port of Arkg.kt — offline ARKG-P256 key derivation and verification.
// Reference: early-access-program/quickstart/android/.../math/Arkg.kt

import Foundation
import CryptoKit

// MARK: - Error types

enum ARKGError: Error, LocalizedError {
    case noGeneratedKey
    case noSignature
    case contextTooLong
    case expandMessageXmdInputTooLarge

    var errorDescription: String? {
        switch self {
        case .noGeneratedKey:               return "No generated key in registration response"
        case .noSignature:                  return "No signature in assertion response"
        case .contextTooLong:               return "Context must be at most 64 bytes"
        case .expandMessageXmdInputTooLarge: return "expand_message_xmd: input size out of range"
        }
    }
}

// MARK: - ARKG

enum ARKG {
    // DST strings (RFC 9380 domain separation)
    static let dstExt    = Data("ARKG-P256".utf8)
    static let kemDstExt = Data("ARKG-ECDH.ARKG-P256".utf8)
    static let hashToFieldL = 48   // bytes of XMD output per field element

    // MARK: - Public API

    /// Offline derivation of one ARKG-P256 public key.
    /// - Parameters:
    ///   - pkKem: KEM public key (65-byte uncompressed P-256 point, 04 || x || y)
    ///   - pkBl:  Blinding public key (65-byte uncompressed)
    ///   - ikm:   Caller-supplied entropy (≥ 32 bytes recommended)
    ///   - context: Arbitrary context label (≤ 64 bytes)
    /// - Returns: (derived public key 65-byte, ARKG key handle bytes)
    static func derivePublicKey(
        pkKem: Data, pkBl: Data, ikm: Data, context: Data
    ) throws -> (publicKey: Data, arkgKeyHandle: Data) {
        guard context.count <= 64 else { throw ARKGError.contextTooLong }

        let ctxPrime = Data([UInt8(context.count)]) + context
        let ctxBl  = Data("ARKG-Derive-Key-BL.".utf8)  + ctxPrime
        let ctxKem = Data("ARKG-Derive-Key-KEM.".utf8) + ctxPrime

        let (ikmTau, c) = try kemEncaps(pkKem: pkKem, ikm: ikm, ctxKem: ctxKem)
        let tau  = try blPrf(ikmTau: ikmTau, ctxBl: ctxBl)   // 32-byte scalar
        let tauG = try p256ScalarMulG(scalar: tau)             // tau × G
        let pkPrime = try p256Add(pkBl, tauG)                 // pkBl + tau×G

        return (pkPrime, c)
    }

    /// Encode the additionalArgs CBOR map for GetAssertion.
    /// Structure: { 3: -65539, -3: -65700, -2: context, -1: arkgKeyHandle }
    static func buildAdditionalArgs(context: Data, arkgKeyHandle: Data) -> Data {
        MiniCBOR.encode(.map([
            (3,  .int(-65539)),
            (-3, .int(-65700)),
            (-2, .bytes(context)),
            (-1, .bytes(arkgKeyHandle))
        ]))
    }

    /// Offline ECDSA-SHA256 verification against the derived public key.
    /// - Parameters:
    ///   - publicKey:    Derived 65-byte uncompressed P-256 public key (0x04 || x || y)
    ///   - message:      Original message bytes — CryptoKit hashes with SHA-256 internally,
    ///                   matching the SHA-256(message) digest the app sent as `tbs`.
    ///   - derSignature: DER-encoded ECDSA signature from the assertion response.
    static func verifySignature(publicKey: Data, message: Data, derSignature: Data) throws -> Bool {
        let pubKey = try P256.Signing.PublicKey(x963Representation: publicKey)
        let sig    = try P256.Signing.ECDSASignature(derRepresentation: derSignature)
        return pubKey.isValidSignature(sig, for: message)
    }

    // MARK: - KEM (ARKG-KEM-HMAC)

    private static func kemEncaps(
        pkKem: Data, ikm: Data, ctxKem: Data
    ) throws -> (ikmTau: Data, c: Data) {
        let ctxSub = Data("ARKG-KEM-HMAC.".utf8) + ctxKem
        let (kPrime, cPrime) = try subKemEncaps(pkKem: pkKem, ikm: ikm, ctx: ctxSub)

        let mk = hkdfSha256(
            ikm:    kPrime,
            info:   Data("ARKG-KEM-HMAC-mac.".utf8) + kemDstExt + ctxKem,
            length: 32
        )
        let fullMac = hmacSha256(key: mk, message: cPrime)
        let tau = Data(fullMac.prefix(16))   // truncated MAC used as key handle prefix

        let k = hkdfSha256(
            ikm:    kPrime,
            info:   Data("ARKG-KEM-HMAC-shared.".utf8) + kemDstExt + ctxKem,
            length: kPrime.count
        )
        return (k, tau + cPrime)   // keyHandle = truncatedMAC || ephemeralPubKey
    }

    private static func subKemEncaps(
        pkKem: Data, ikm: Data, ctx: Data
    ) throws -> (k: Data, cPrime: Data) {
        let (pkPrime, skPrime) = try subKemDeriveKeyPair(ikm: ikm)
        let k = try p256ECDH(privateScalar: skPrime, publicPoint: pkKem)
        return (k, pkPrime)
    }

    private static func subKemDeriveKeyPair(ikm: Data) throws -> (pk: Data, sk: Data) {
        let dst  = Data("ARKG-KEM-ECDH-KG.".utf8) + kemDstExt
        let sk   = try hashToField(msg: ikm, count: 1, dst: dst)[0]   // 32-byte scalar mod n
        let pk   = try p256ScalarMulG(scalar: sk)
        return (pk, sk)
    }

    // MARK: - Blinding PRF

    private static func blPrf(ikmTau: Data, ctxBl: Data) throws -> Data {
        let dst = Data("ARKG-BL-EC.".utf8) + dstExt + ctxBl
        return try hashToField(msg: ikmTau, count: 1, dst: dst)[0]
    }

    // MARK: - Hash to field (RFC 9380)

    /// Returns `count` 32-byte scalars, each an element of Z/nZ.
    private static func hashToField(msg: Data, count: Int, dst: Data) throws -> [Data] {
        let expanded = try expandMessageXmd(msg: msg, lenInBytes: count * hashToFieldL, dst: dst)
        return (0..<count).map { i in
            let slice = expanded[(i * hashToFieldL) ..< ((i + 1) * hashToFieldL)]
            return p256ModN48(Data(slice))   // reduce 48-byte big-endian value mod n
        }
    }

    /// RFC 9380 §5.4.1 expand_message_xmd with SHA-256.
    private static func expandMessageXmd(msg: Data, lenInBytes: Int, dst: Data) throws -> Data {
        let bInBytes = 32   // SHA-256 output
        let sInBytes = 64   // SHA-256 block size
        let ell = (lenInBytes + bInBytes - 1) / bInBytes
        guard ell <= 255, lenInBytes <= 65535, dst.count <= 255 else {
            throw ARKGError.expandMessageXmdInputTooLarge
        }

        let dstPrime = dst + Data([UInt8(dst.count)])
        let zPad     = Data(repeating: 0, count: sInBytes)
        let lIBStr   = Data([UInt8((lenInBytes >> 8) & 0xFF), UInt8(lenInBytes & 0xFF)])
        let msgPrime = zPad + msg + lIBStr + Data([0x00]) + dstPrime

        let b0 = Data(SHA256.hash(data: msgPrime))
        var bXor = b0                     // for i=1: input = b0 || [1] || dstPrime
        var uniformBytes = Data()

        for i in 1...ell {
            let input = bXor + Data([UInt8(i)]) + dstPrime
            let bi = Data(SHA256.hash(data: input))
            uniformBytes += bi
            bXor = xorBytes(b0, bi)       // for i+1: input = (b0 XOR b_i) || [i+1] || dstPrime
        }

        return Data(uniformBytes.prefix(lenInBytes))
    }

    // MARK: - Symmetric crypto

    /// HKDF-SHA256 with null salt (32 zero bytes per RFC 5869 §2.2).
    private static func hkdfSha256(ikm: Data, info: Data, length: Int) -> Data {
        let salt = Data(repeating: 0, count: 32)
        let key  = HKDF<SHA256>.deriveKey(
            inputKeyMaterial: SymmetricKey(data: ikm),
            salt:             salt,
            info:             info,
            outputByteCount:  length
        )
        return key.withUnsafeBytes { Data($0) }
    }

    /// HMAC-SHA256.
    private static func hmacSha256(key: Data, message: Data) -> Data {
        Data(HMAC<SHA256>.authenticationCode(for: message, using: SymmetricKey(data: key)))
    }

    // MARK: - EC primitives (backed by CryptoKit + P256Arithmetic)

    /// Compute scalar × G → 65-byte uncompressed P-256 point.
    private static func p256ScalarMulG(scalar: Data) throws -> Data {
        let priv = try P256.KeyAgreement.PrivateKey(rawRepresentation: scalar)
        return priv.publicKey.x963Representation   // 04 || x || y
    }

    /// ECDH: x-coordinate of (privateScalar × publicPoint), 32 bytes big-endian.
    private static func p256ECDH(privateScalar: Data, publicPoint: Data) throws -> Data {
        let priv = try P256.KeyAgreement.PrivateKey(rawRepresentation: privateScalar)
        let pub  = try P256.KeyAgreement.PublicKey(x963Representation: publicPoint)
        return try priv.sharedSecretFromKeyAgreement(with: pub).withUnsafeBytes { Data($0) }
    }
}

// MARK: - Helpers

private func xorBytes(_ a: Data, _ b: Data) -> Data {
    precondition(a.count == b.count)
    return Data(zip(a, b).map { $0 ^ $1 })
}
