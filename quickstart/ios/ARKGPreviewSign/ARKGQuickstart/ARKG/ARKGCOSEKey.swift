// ARKGCOSEKey.swift
// Parses the custom COSE key structure returned by previewSign's GeneratedKey.
// Key type kty=-65537 / alg=-65700 (ARKG-P256); not supported by CryptoKit or
// the standard COSE implementation in YubiKit, so we decode it manually.
//
// Structure (from Credentials.kt):
//   { 1: -65537,        (kty: ARKG split-EC)
//     3: -65700,        (alg: ARKG-P256)
//    -3: -9,            (deriveKeyAlg)
//    -1: <EC2 map>,     (pkBl – blinding key)
//    -2: <EC2 map> }    (pkKem – encapsulation key)
//
// Each sub-map:
//   { 1: 2,  (kty: EC2)
//     3: alg,
//    -1: crv,
//    -2: x_bytes,
//    -3: y_bytes }

import Foundation

enum ARKGCOSEKeyError: Error, LocalizedError {
    case decodeFailed(String)

    var errorDescription: String? {
        if case .decodeFailed(let msg) = self { return "ARKGCOSEKey decode error: \(msg)" }
        return nil
    }
}

enum ARKGCOSEKey {

    /// Parse a CBOR-encoded GeneratedKey.publicKey.
    /// - Returns: (pkBl, pkKem) as 65-byte uncompressed P-256 points (04 || x || y)
    static func parse(_ cborData: Data) throws -> (pkBl: Data, pkKem: Data) {
        let top = try MiniCBOR.decode(cborData)
        let topPairs = try requireMap(top, label: "top-level")

        guard let ktyVal = lookup(topPairs, key: 1),
              case .int(let kty) = ktyVal, kty == -65537 else {
            throw err("expected kty = -65537")
        }
        guard let algVal = lookup(topPairs, key: 3),
              case .int(let alg) = algVal, alg == -65700 else {
            throw err("expected alg = -65700")
        }

        guard let pkBlVal  = lookup(topPairs, key: -1) else { throw err("missing pkBl (-1)") }
        guard let pkKemVal = lookup(topPairs, key: -2) else { throw err("missing pkKem (-2)") }

        let pkBl  = try uncompressedPoint(from: pkBlVal,  label: "pkBl")
        let pkKem = try uncompressedPoint(from: pkKemVal, label: "pkKem")

        return (pkBl, pkKem)
    }

    // MARK: - Helpers

    private static func uncompressedPoint(from value: MiniCBOR.Value, label: String) throws -> Data {
        let pairs = try requireMap(value, label: label)
        guard let xVal = lookup(pairs, key: -2), case .bytes(let x) = xVal else {
            throw err("\(label): missing x (-2)")
        }
        guard let yVal = lookup(pairs, key: -3), case .bytes(let y) = yVal else {
            throw err("\(label): missing y (-3)")
        }
        guard x.count == 32, y.count == 32 else {
            throw err("\(label): x/y must be 32 bytes each")
        }
        return Data([0x04]) + x + y
    }

    private static func requireMap(
        _ value: MiniCBOR.Value, label: String
    ) throws -> [(Int, MiniCBOR.Value)] {
        guard case .map(let pairs) = value else { throw err("\(label): expected CBOR map") }
        return pairs
    }

    private static func lookup(_ pairs: [(Int, MiniCBOR.Value)], key: Int) -> MiniCBOR.Value? {
        pairs.first(where: { $0.0 == key })?.1
    }

    private static func err(_ msg: String) -> ARKGCOSEKeyError { .decodeFailed(msg) }
}
