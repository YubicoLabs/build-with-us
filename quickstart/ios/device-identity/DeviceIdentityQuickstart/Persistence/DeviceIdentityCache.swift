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

/// A non-sensitive, on-disk cache of what a platform learns about a YubiKey once it holds a PPUAT:
/// the stable device identifier, the current credential-store state, and the enumerated credential
/// list. It implements the `encCredStoreState` cache-invalidation pattern from the .NET reference:
///
/// ```
/// saved state == current state  ->  cache is valid, skip enumeration
/// saved state != current state  ->  re-enumerate credentials
/// ```
///
/// Stored in `UserDefaults` because none of it is secret (unlike the PPUAT, which lives in the
/// Keychain via ``KeychainPpuatStore``).
struct DeviceIdentityCache: Codable, Sendable, Equatable {

    struct Credential: Codable, Sendable, Equatable, Identifiable {
        var id: String { credentialIdHex }
        let credentialIdHex: String
        let userName: String?
        let userDisplayName: String?
    }

    struct RelyingParty: Codable, Sendable, Equatable, Identifiable {
        var id: String { rpId }
        let rpId: String
        let rpName: String?
        let credentials: [Credential]
    }

    /// Hex of the decrypted `encIdentifier` — stable across PIN changes for one authenticator.
    let deviceIdHex: String
    /// Hex of the decrypted `encCredStoreState` at the time the credential list was captured.
    let credStoreStateHex: String
    let existingCredentialsCount: Int
    let maxRemainingCredentialsCount: Int
    let relyingParties: [RelyingParty]

    // MARK: - Persistence

    private static let key = "com.yubico.device-identity-quickstart.cache"

    static func load(for deviceIdHex: String) -> DeviceIdentityCache? {
        guard let data = UserDefaults.standard.data(forKey: key),
            let cache = try? JSONDecoder().decode(DeviceIdentityCache.self, from: data),
            cache.deviceIdHex == deviceIdHex
        else {
            return nil
        }
        return cache
    }

    func save() {
        if let data = try? JSONEncoder().encode(self) {
            UserDefaults.standard.set(data, forKey: Self.key)
        }
    }

    static func clear() {
        UserDefaults.standard.removeObject(forKey: key)
    }
}
