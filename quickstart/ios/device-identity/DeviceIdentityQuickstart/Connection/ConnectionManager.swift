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

/// The transports a user can pick on iOS. macOS always uses the wired FIDO/HID connection.
enum PPTransport: String, CaseIterable, Identifiable, Sendable {
    case nfc
    case wired

    var id: String { rawValue }

    var label: String {
        switch self {
        case .nfc: return "NFC (tap)"
        case .wired: return "USB / Lightning"
        }
    }
}

/// Opens a direct `CTAP2.Session` to a YubiKey — no FidoUI wrapper, since acquiring a PPUAT is a
/// raw CTAP2 ClientPIN operation rather than a WebAuthn ceremony.
///
/// - iOS: NFC via `NFCSmartCardConnection`, or wired (USB-C / Lightning) via
///   `WiredSmartCardConnection` — both `SmartCardConnection`s.
/// - macOS: `HIDFIDOConnection` (a `FIDOConnection`) over USB HID.
enum ConnectionManager {

    /// A live session plus the connection backing it, so the caller can close it when done.
    struct Opened {
        let session: CTAP2.Session
        private let connection: any Connection

        init(session: CTAP2.Session, connection: any Connection) {
            self.session = session
            self.connection = connection
        }

        func close() async {
            await connection.close(error: nil)
        }
    }

    static func open(transport: PPTransport, nfcAlertMessage: String) async throws -> Opened {
        #if os(iOS)
        switch transport {
        case .nfc:
            let connection = try await NFCSmartCardConnection(alertMessage: nfcAlertMessage)
            let session = try await CTAP2.Session.makeSession(connection: connection)
            return Opened(session: session, connection: connection)
        case .wired:
            let connection = try await WiredSmartCardConnection.makeConnection()
            let session = try await CTAP2.Session.makeSession(connection: connection)
            return Opened(session: session, connection: connection)
        }
        #elseif os(macOS)
        let connection = try await HIDFIDOConnection.makeConnection()
        let session = try await CTAP2.Session.makeSession(connection: connection)
        return Opened(session: session, connection: connection)
        #endif
    }
}
