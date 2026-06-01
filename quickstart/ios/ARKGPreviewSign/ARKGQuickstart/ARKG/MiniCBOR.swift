// MiniCBOR.swift
// Minimal standalone CBOR encoder/decoder for ARKG use.
// Handles integer keys/values and byte-string values in maps.
// YubiKit's CBOR types are internal to that module, so we maintain our own.

import Foundation

enum MiniCBOR {

    enum Value {
        case int(Int)
        case bytes(Data)
        case map([(Int, Value)])   // ordered pairs; encode() sorts by canonical key bytes
    }

    enum DecodeError: Error {
        case truncated
        case unexpectedMajorType(UInt8)
        case negativeKeyInMap
    }

    // MARK: - Encode

    static func encode(_ value: Value) -> Data {
        var out = Data()
        encodeValue(value, into: &out)
        return out
    }

    private static func encodeValue(_ value: Value, into out: inout Data) {
        switch value {
        case .int(let n):   encodeInt(n, into: &out)
        case .bytes(let d): encodeLength(d.count, majorType: 2, into: &out); out += d
        case .map(let pairs):
            let sorted = pairs.sorted {
                encodedInt($0.0).lexicographicallyPrecedes(encodedInt($1.0))
            }
            encodeLength(sorted.count, majorType: 5, into: &out)
            for (k, v) in sorted { encodeInt(k, into: &out); encodeValue(v, into: &out) }
        }
    }

    private static func encodeInt(_ n: Int, into out: inout Data) {
        if n >= 0 { encodeLength(n, majorType: 0, into: &out) }
        else      { encodeLength(-1 - n, majorType: 1, into: &out) }
    }

    private static func encodedInt(_ n: Int) -> Data {
        var d = Data(); encodeInt(n, into: &d); return d
    }

    private static func encodeLength(_ n: Int, majorType: Int, into out: inout Data) {
        let head = UInt8(majorType << 5)
        if n <= 23 {
            out.append(head | UInt8(n))
        } else if n <= 0xFF {
            out.append(head | 24); out.append(UInt8(n))
        } else if n <= 0xFFFF {
            out.append(head | 25)
            out.append(UInt8((n >> 8) & 0xFF)); out.append(UInt8(n & 0xFF))
        } else {
            out.append(head | 26)
            out.append(UInt8((n >> 24) & 0xFF)); out.append(UInt8((n >> 16) & 0xFF))
            out.append(UInt8((n >> 8)  & 0xFF)); out.append(UInt8(n & 0xFF))
        }
    }

    // MARK: - Decode

    static func decode(_ data: Data) throws -> Value {
        var offset = 0
        return try decodeValue(data, offset: &offset)
    }

    private static func decodeValue(_ data: Data, offset: inout Int) throws -> Value {
        guard offset < data.count else { throw DecodeError.truncated }
        let head = data[offset]; offset += 1
        let majorType = (head >> 5) & 0x7
        let addInfo   = head & 0x1F
        let n = try decodeArg(addInfo, data, offset: &offset)

        switch majorType {
        case 0:
            return .int(Int(n))
        case 1:
            return .int(-1 - Int(n))
        case 2:
            let end = offset + Int(n)
            guard end <= data.count else { throw DecodeError.truncated }
            let bytes = Data(data[offset..<end]); offset = end
            return .bytes(bytes)
        case 5:
            var pairs: [(Int, Value)] = []
            for _ in 0..<Int(n) {
                let kv = try decodeValue(data, offset: &offset)
                guard case .int(let k) = kv else { throw DecodeError.negativeKeyInMap }
                let v = try decodeValue(data, offset: &offset)
                pairs.append((k, v))
            }
            return .map(pairs)
        default:
            throw DecodeError.unexpectedMajorType(majorType)
        }
    }

    private static func decodeArg(_ addInfo: UInt8, _ data: Data, offset: inout Int) throws -> UInt64 {
        if addInfo < 24 { return UInt64(addInfo) }
        switch addInfo {
        case 24:
            guard offset < data.count else { throw DecodeError.truncated }
            let v = UInt64(data[offset]); offset += 1; return v
        case 25:
            guard offset + 2 <= data.count else { throw DecodeError.truncated }
            let v = UInt64(data[offset]) << 8 | UInt64(data[offset + 1]); offset += 2; return v
        case 26:
            guard offset + 4 <= data.count else { throw DecodeError.truncated }
            let v = UInt64(data[offset]) << 24 | UInt64(data[offset+1]) << 16
                  | UInt64(data[offset+2]) << 8  | UInt64(data[offset+3])
            offset += 4; return v
        default:
            throw DecodeError.unexpectedMajorType(addInfo)
        }
    }
}
