// Copyright Yubico AB
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import Foundation
import Security
import YubiKit

/// A ``PpuatStore`` backed by the system Keychain — the iOS/macOS analog of the .NET reference's
/// DPAPI-protected on-disk store.
///
/// The token is stored as a generic-password item with
/// `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, so it never leaves the device and is not
/// included in backups or iCloud Keychain. This is the appropriate protection class for
/// sensitive, device-bound key material like a PPUAT.
///
/// The stored blob is `[protocolVersion byte] + rawTokenBytes`, reconstructed on load with
/// ``CTAP2/Token/init(rawValue:protocolVersion:)``.
struct KeychainPpuatStore: PpuatStore {

    /// Errors surfaced by Keychain operations, wrapping the underlying `OSStatus`.
    enum StoreError: Error, CustomStringConvertible {
        case unexpectedStatus(OSStatus)
        case corruptData

        var description: String {
            switch self {
            case .unexpectedStatus(let status):
                let message = SecCopyErrorMessageString(status, nil) as String? ?? "unknown"
                return "Keychain error \(status): \(message)"
            case .corruptData:
                return "Stored token data is corrupt."
            }
        }
    }

    let service: String
    let account: String

    init(service: String = "com.yubico.device-identity-quickstart.ppuat", account: String = "default") {
        self.service = service
        self.account = account
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    func save(_ token: CTAP2.Token) throws {
        var blob = Data([UInt8(token.protocolVersion.rawValue)])
        blob.append(token.rawValue)

        // Replace any existing item so re-acquiring overwrites cleanly.
        SecItemDelete(baseQuery as CFDictionary)

        var attributes = baseQuery
        attributes[kSecValueData as String] = blob
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly

        let status = SecItemAdd(attributes as CFDictionary, nil)
        guard status == errSecSuccess else { throw StoreError.unexpectedStatus(status) }
    }

    func load() throws -> CTAP2.Token? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)

        switch status {
        case errSecItemNotFound:
            return nil
        case errSecSuccess:
            break
        default:
            throw StoreError.unexpectedStatus(status)
        }

        guard let blob = item as? Data, blob.count >= 2,
            let version = CTAP2.ClientPin.ProtocolVersion(rawValue: Int(blob[blob.startIndex]))
        else {
            throw StoreError.corruptData
        }

        let rawValue = blob.subdata(in: (blob.startIndex + 1)..<blob.endIndex)
        guard let token = CTAP2.Token(rawValue: rawValue, protocolVersion: version) else {
            throw StoreError.corruptData
        }
        return token
    }

    func clear() throws {
        let status = SecItemDelete(baseQuery as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw StoreError.unexpectedStatus(status)
        }
    }
}
