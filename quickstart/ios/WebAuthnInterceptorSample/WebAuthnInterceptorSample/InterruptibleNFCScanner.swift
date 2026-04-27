/// Custom NFC scanner that can be programmatically cancelled before a tag is tapped.
///
/// `NFCSmartCardConnection` from the SDK routes everything through a private singleton,
/// making it impossible to close the scanning dialog from outside. This implementation
/// manages its own `NFCTagReaderSession` and exposes `cancel()` so the USB-C hotplug
/// task can dismiss the NFC dialog at any time—even before the user taps.

#if os(iOS)
import CoreNFC
import YubiKit

// MARK: - InterruptibleNFCConnection

/// A `SmartCardConnection` wrapping a raw `NFCISO7816Tag`.
///
/// Created by `InterruptibleNFCScanner` after a successful tag tap and connect.
/// APDUs are forwarded directly to the tag; closing invalidates the owning session.
struct InterruptibleNFCConnection: SmartCardConnection, @unchecked Sendable {
    // Tag is @objc and not formally Sendable; safe here because CTAP2 sends APDUs
    // sequentially and we never access the tag concurrently.
    let tag: any NFCISO7816Tag
    let session: NFCTagReaderSession

    // MARK: SmartCardConnection – data path

    func send(data: Data) async throws(SmartCardConnectionError) -> Data {
        guard let apdu = NFCISO7816APDU(data: data) else {
            throw SmartCardConnectionError.malformedData("Invalid APDU format")
        }
        do {
            return try await withCheckedThrowingContinuation { cont in
                tag.sendCommand(apdu: apdu) { responseData, sw1, sw2, error in
                    if let error {
                        cont.resume(throwing: error)
                    } else {
                        cont.resume(returning: responseData + Data([sw1, sw2]))
                    }
                }
            }
        } catch {
            throw SmartCardConnectionError.transmitFailed("NFC transmit failed", error)
        }
    }

    // MARK: Connection – lifecycle

    func close(error: Error?) async {
        // NFCTagReaderSession.invalidate() is documented as thread-safe.
        if let error {
            session.invalidate(errorMessage: error.localizedDescription)
        } else {
            session.invalidate()
        }
    }

    func waitUntilClosed() async -> Error? {
        // The WebAuthnHandler manages connection lifetime explicitly via closeConnection();
        // waitUntilClosed() is not required for correct operation in this sample.
        return nil
    }
}

// MARK: SmartCardConnection – unused factory requirements

extension InterruptibleNFCConnection {
    init() async throws(SmartCardConnectionError) {
        throw SmartCardConnectionError.unsupported
    }

    static func makeConnection() async throws(SmartCardConnectionError) -> InterruptibleNFCConnection {
        throw SmartCardConnectionError.unsupported
    }
}

// MARK: - InterruptibleNFCScanner

/// Manages an `NFCTagReaderSession` with full programmatic cancel support.
///
/// Unlike `NFCSmartCardConnection`, calling `cancel()` at any point—even before the
/// user taps—immediately calls `session.invalidate()`, dismissing the NFC dialog.
final class InterruptibleNFCScanner: NSObject, @unchecked Sendable {

    // All mutable state is serialized through this queue (same pattern as the SDK).
    private let queue = DispatchQueue(label: "com.yubikey.interruptible-nfc")
    private var session: NFCTagReaderSession?
    private var continuation: CheckedContinuation<InterruptibleNFCConnection, Error>?

    // MARK: - Public API

    /// Shows the NFC scanning dialog and suspends until a tag is tapped and connected,
    /// or until `cancel()` is called (or the session is dismissed/times out).
    func scan(alertMessage: String) async throws -> InterruptibleNFCConnection {
        try await withCheckedThrowingContinuation { cont in
            queue.async {
                guard self.continuation == nil else {
                    cont.resume(throwing: SmartCardConnectionError.busy)
                    return
                }
                guard let session = NFCTagReaderSession(
                    pollingOption: [.iso14443],
                    delegate: self,
                    queue: self.queue
                ) else {
                    cont.resume(
                        throwing: SmartCardConnectionError.pollingFailed("Failed to create NFC session")
                    )
                    return
                }
                self.continuation = cont
                self.session = session
                if !alertMessage.isEmpty { session.alertMessage = alertMessage }
                session.begin()
            }
        }
    }

    /// Dismisses the NFC scanning dialog immediately, causing `scan()` to throw.
    ///
    /// Safe to call before or after a tag tap, and from any thread or Task.
    func cancel() {
        queue.async {
            self.resumeAndInvalidate(
                with: .failure(SmartCardConnectionError.cancelledByUser)
            )
        }
    }

    // MARK: - Private

    /// Resumes the pending continuation (once) and invalidates the session.
    /// Must be called on `queue`.
    private func resumeAndInvalidate(with result: Result<InterruptibleNFCConnection, Error>) {
        session?.invalidate()
        session = nil
        continuation?.resume(with: result)
        continuation = nil
    }
}

// MARK: - NFCTagReaderSessionDelegate

extension InterruptibleNFCScanner: NFCTagReaderSessionDelegate {

    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {}

    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard let first = tags.first, case .iso7816(let iso7816Tag) = first else { return }

        // Connect to the tag before handing the connection to the caller.
        session.connect(to: first) { [weak self] error in
            guard let self else { return }
            self.queue.async {
                if let error {
                    self.resumeAndInvalidate(with: .failure(error))
                } else {
                    let conn = InterruptibleNFCConnection(tag: iso7816Tag, session: session)
                    // Don't invalidate the session here — the connection owns it from now on.
                    self.continuation?.resume(returning: conn)
                    self.continuation = nil
                    self.session = nil
                }
            }
        }
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        queue.async {
            guard self.continuation != nil else { return } // Already resumed (e.g. after connect)
            let nfcError = error as? NFCReaderError
            let mapped: SmartCardConnectionError
            switch nfcError?.code {
            case .some(.readerSessionInvalidationErrorUserCanceled):
                mapped = .cancelledByUser
            case .some(.readerSessionInvalidationErrorSessionTimeout):
                mapped = .cancelled
            default:
                mapped = .setupFailed("NFC session invalidated", error)
            }
            self.resumeAndInvalidate(with: .failure(mapped))
        }
    }
}

#endif
