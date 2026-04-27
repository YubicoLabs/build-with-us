/// Receives WebAuthn requests from JS, delegates to WebAuthn.Client.

import Foundation
import YubiKit
#if os(iOS)
import ExternalAccessory
#endif

// MARK: - Request Types

struct CreateRequest: Decodable {
    let origin: String
    let publicKey: WebAuthn.Registration.Options

    enum CodingKeys: String, CodingKey {
        case origin
        case publicKey = "request"
    }
}

struct GetRequest: Decodable {
    let origin: String
    let publicKey: WebAuthn.Authentication.Options

    enum CodingKeys: String, CodingKey {
        case origin
        case publicKey = "request"
    }
}

// MARK: - NFC PIN Retry

/// Thrown by handleStream when NFC was closed to allow PIN entry.
/// The caller should open a new NFC session and retry the operation with the captured PIN.
private struct NFCPINRetry: Error {
    let pin: String
}

// MARK: - WebAuthnHandler

actor WebAuthnHandler {

    private var connection: (any Connection)?
    private let pinProvider: @Sendable () async -> String?
    private let accountPicker: @Sendable ([WebAuthn.Authentication.MatchedCredential]) async -> Int

    // TODO: Add PublicSuffixList integration. For now, we don't validate against PSL.
    private let isPublicSuffix: WebAuthn.PublicSuffixChecker = { _ in false }

    init(
        pinProvider: @escaping @Sendable () async -> String?,
        accountPicker: @escaping @Sendable ([WebAuthn.Authentication.MatchedCredential]) async -> Int = { _ in 0 }
    ) {
        self.pinProvider = pinProvider
        self.accountPicker = accountPicker
    }

    // MARK: - Public API

    func handleCreate(_ data: Data) async throws -> String {
        let request = try JSONDecoder().decode(CreateRequest.self, from: data)

        do {
            defer { Task { await closeConnection() } }
            let session = try await makeSession()
            let client = WebAuthn.Client(
                session: session,
                origin: try .init(request.origin),
                isPublicSuffix: isPublicSuffix
            )
            let stream = await client.makeCredential(request.publicKey)
            let response = try await handleStream(stream)
            return String(decoding: try response.toJSON(), as: UTF8.self)
        } catch let retry as NFCPINRetry {
            // NFC was closed to allow PIN entry. Reconnect for the second tap.
            defer { Task { await closeConnection() } }
            let session = try await makeSession(alertMessage: "Tap YubiKey again to complete")
            let client = WebAuthn.Client(
                session: session,
                origin: try .init(request.origin),
                isPublicSuffix: isPublicSuffix
            )
            let response = try await client.makeCredential(request.publicKey).value(pin: retry.pin)
            return String(decoding: try response.toJSON(), as: UTF8.self)
        }
    }

    func handleGet(_ data: Data) async throws -> String {
        let request = try JSONDecoder().decode(GetRequest.self, from: data)

        do {
            defer { Task { await closeConnection() } }
            let session = try await makeSession()
            let client = WebAuthn.Client(
                session: session,
                origin: try .init(request.origin),
                isPublicSuffix: isPublicSuffix
            )
            let stream = await client.getAssertion(request.publicKey)
            let matches = try await handleStream(stream)
            let selected = matches.count == 1 ? 0 : await accountPicker(matches)
            let response = try await matches[selected].select()
            return String(decoding: try response.toJSON(), as: UTF8.self)
        } catch let retry as NFCPINRetry {
            // NFC was closed to allow PIN entry. Reconnect for the second tap.
            defer { Task { await closeConnection() } }
            let session = try await makeSession(alertMessage: "Tap YubiKey again to authenticate")
            let client = WebAuthn.Client(
                session: session,
                origin: try .init(request.origin),
                isPublicSuffix: isPublicSuffix
            )
            let matches = try await client.getAssertion(request.publicKey).value(pin: retry.pin)
            let selected = matches.count == 1 ? 0 : await accountPicker(matches)
            let response = try await matches[selected].select()
            return String(decoding: try response.toJSON(), as: UTF8.self)
        }
    }

    // MARK: - Stream Handling

    private func handleStream<R: Sendable>(
        _ stream: WebAuthn.StatusStream<R>
    ) async throws -> R {
        // Tracks the PIN collected during an NFC session that was closed for PIN entry,
        // so we can surface an NFCPINRetry if the stream subsequently fails.
        var nfcPINForRetry: String? = nil

        do {
            for try await status in stream {
                switch status {
                case .requestingPIN(let submitPIN):
                    // NFC dialog blocks PIN entry UI. Close it first so the sheet can appear.
                    // Check both connection types: NFCSmartCardConnection (iOS < 16) and
                    // InterruptibleNFCConnection (iOS 16+, our custom scanner).
                    let isNFC: Bool
                    #if os(iOS)
                    isNFC = connection is NFCSmartCardConnection
                        || connection is InterruptibleNFCConnection
                    #else
                    isNFC = false
                    #endif
                    if isNFC {
                        await closeConnection()
                    }
                    let pin = await pinProvider()
                    if isNFC, let pin {
                        // Remember PIN so the caller can reconnect and retry.
                        nfcPINForRetry = pin
                    }
                    submitPIN(pin)
                    // Submitting PIN with a closed NFC connection will fail with
                    // connectionLost; we catch that below and convert to NFCPINRetry.
                case .requestingUV(let useUV):
                    useUV(true)
                case .finished(let response):
                    return response
                default:
                    break
                }
            }
        } catch {
            if let pin = nfcPINForRetry {
                throw NFCPINRetry(pin: pin)
            }
            throw error
        }
        preconditionFailure("Stream ended without response")
    }

    // MARK: - Connection Management

    #if os(iOS)
    /// Returns a CTAP2 session over the best available connection.
    ///
    /// - If a USB-C YubiKey is already plugged in, connects via USB-C immediately.
    /// - Otherwise shows the NFC scanning dialog and races it against USB-C hotplug
    ///   (checked every second). Whichever connects first wins; the other is cancelled.
    private func makeSession(alertMessage: String = "Tap or Insert your YubiKey") async throws -> CTAP2.Session {
        let conn: any SmartCardConnection
        if #available(iOS 16, *) {
            conn = try await connectToYubiKey(alertMessage: alertMessage)
        } else {
            conn = try await NFCSmartCardConnection(alertMessage: alertMessage)
        }
        connection = conn
        return try await CTAP2.Session.makeSession(connection: conn)
    }

    @available(iOS 16, *)
    private func connectToYubiKey(alertMessage: String) async throws -> any SmartCardConnection {
        // Prefer USB-C if a YubiKey is already connected.
        let existing = (try? await USBSmartCardConnection.availableDevices()) ?? []
        if !existing.isEmpty {
            return try await USBSmartCardConnection()
        }

        // Prefer Lightning if a YubiKey is already plugged in.
        if hasConnectedLightningKey() {
            return try await LightningSmartCardConnection()
        }

        // Race NFC scanning against USB-C and Lightning hotplug detection.
        //
        // InterruptibleNFCScanner manages its own NFCTagReaderSession and exposes cancel(),
        // which calls session.invalidate() directly. This lets the wired tasks dismiss the
        // NFC dialog programmatically—even before the user taps—something the SDK's
        // NFCSmartCardConnection cannot do (it routes through a private singleton).
        let nfcScanner = InterruptibleNFCScanner()

        // Use optional elements so a cancelled NFC task doesn't fail the whole group.
        return try await withThrowingTaskGroup(of: (any SmartCardConnection)?.self) { group in

            // NFC task: swallow errors caused by cancel() so wired connections can win cleanly.
            group.addTask {
                (try? await nfcScanner.scan(alertMessage: alertMessage)) as (any SmartCardConnection)?
            }

            // USB-C polling task: checks every second for a newly inserted YubiKey.
            // On detection it cancels NFC (closing the dialog) then returns USB-C.
            group.addTask {
                while true {
                    try Task.checkCancellation()
                    try await Task.sleep(for: .seconds(1))
                    let devices = (try? await USBSmartCardConnection.availableDevices()) ?? []
                    if !devices.isEmpty {
                        nfcScanner.cancel() // dismiss the NFC dialog immediately
                        return try await USBSmartCardConnection() as (any SmartCardConnection)?
                    }
                }
            }

            // Lightning polling task: checks every second for a newly inserted YubiKey.
            // USB-C and Lightning ports are mutually exclusive per device, so this task
            // and the USB-C task will never both detect a key at the same time.
            group.addTask {
                while true {
                    try Task.checkCancellation()
                    try await Task.sleep(for: .seconds(1))
                    if await self.hasConnectedLightningKey() {
                        nfcScanner.cancel() // dismiss the NFC dialog immediately
                        return try await LightningSmartCardConnection() as (any SmartCardConnection)?
                    }
                }
            }

            // Return the first non-nil result (nil = NFC was cancelled, keep waiting).
            while let taskResult = try await group.next() {
                if let conn = taskResult {
                    group.cancelAll()
                    return conn
                }
            }
            throw SmartCardConnectionError.cancelled
        }
    }

    @available(iOS 16, *)
    private func hasConnectedLightningKey() -> Bool {
        EAAccessoryManager.shared().connectedAccessories.contains {
            $0.protocolStrings.contains("com.yubico.ylp") && $0.manufacturer == "Yubico"
        }
    }
    #else
    private func makeSession(alertMessage: String = "") async throws -> CTAP2.Session {
        let conn = try await HIDFIDOConnection()
        connection = conn
        return try await CTAP2.Session.makeSession(connection: conn)
    }
    #endif

    private func closeConnection(message: String? = nil) async {
        #if os(iOS)
        if let nfc = connection as? NFCSmartCardConnection {
            await nfc.close(message: message)
        } else {
            await connection?.close(error: nil)
        }
        #else
        await connection?.close(error: nil)
        #endif
        connection = nil
    }
}
