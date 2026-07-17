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
import YubiKit

/// A persistence seam for a Persistent PIN/UV Auth Token (PPUAT).
///
/// This mirrors the role of the .NET reference's on-disk token store: acquire the token once
/// (one PIN entry), persist its raw bytes, and reload it on a later launch to reuse it with no
/// PIN prompt. The default implementation is ``KeychainPpuatStore``.
protocol PpuatStore: Sendable {
    /// Persists a token's raw bytes + protocol version.
    func save(_ token: CTAP2.Token) throws

    /// Reloads a previously saved token, or `nil` if none is stored.
    func load() throws -> CTAP2.Token?

    /// Removes any stored token (e.g. after the key rejects it because the PIN changed).
    func clear() throws
}
