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
import SwiftUI
import YubiKit

/// Whether the enumerated credential list came from the key or from the `encCredStoreState` cache.
enum CacheStatus: Equatable {
    /// No prior cache existed for this device — credentials were enumerated and cached.
    case firstRun
    /// Cached state matched the key's current `encCredStoreState` — enumeration was skipped.
    case hit
    /// Cached state differed — credentials were re-enumerated and the cache updated.
    case miss

    var summary: String {
        switch self {
        case .firstRun: return "First run — enumerated and cached."
        case .hit: return "Cache hit — credential store unchanged, enumeration skipped."
        case .miss: return "Cache miss — credential store changed, re-enumerated."
        }
    }
}

/// Result of attempting a `deleteCredential` with the PCMR (read-only) token.
enum ScopeProbeResult: Equatable {
    case notRun(String)
    /// The key rejected the delete — proof PCMR is read-only. Associated value is the CTAP error.
    case rejected(String)
    /// The delete was NOT rejected (unexpected for a PCMR token).
    case unexpectedlyAllowed
}

/// The full outcome of one device-identity run, rendered by the result views.
struct DeviceIdentityReport: Equatable {
    let reusedFromDisk: Bool
    let firmware: String
    let deviceIdHex: String
    let credStoreStateHex: String
    let cacheStatus: CacheStatus
    let existingCredentialsCount: Int
    let maxRemainingCredentialsCount: Int
    let relyingParties: [DeviceIdentityCache.RelyingParty]
    let scopeProbe: ScopeProbeResult
}

@MainActor
final class DeviceIdentityViewModel: ObservableObject {

    enum Phase {
        case idle
        case working(String)
        case unsupported(String)
        case result(DeviceIdentityReport)
        case failed(String)
    }

    /// Thrown internally when a reused token is rejected by the key (PIN changed or key reset).
    private struct StaleTokenError: Error {}

    @Published var phase: Phase = .idle
    @Published var transport: PPTransport = DeviceIdentityViewModel.defaultTransport
    @Published private(set) var hasSavedToken = false

    private let store: any PpuatStore

    init(store: any PpuatStore = KeychainPpuatStore()) {
        self.store = store
        refreshSavedTokenState()
    }

    static var defaultTransport: PPTransport {
        #if os(iOS)
        return .nfc
        #else
        return .wired
        #endif
    }

    func refreshSavedTokenState() {
        hasSavedToken = ((try? store.load()) ?? nil) != nil
    }

    // MARK: - Intents

    /// Session 1: acquire a PPUAT with a single PIN entry, then run the full flow.
    func acquireWithPIN(_ pin: String) {
        Task { await perform(reuse: false, pin: pin) }
    }

    /// Session 2: reuse the token persisted in the Keychain — no PIN prompt.
    func reuseSavedToken() {
        Task { await perform(reuse: true, pin: nil) }
    }

    func reset() {
        phase = .idle
    }

    func clearSavedToken() {
        try? store.clear()
        DeviceIdentityCache.clear()
        refreshSavedTokenState()
        phase = .idle
    }

    // MARK: - Flow

    private func perform(reuse: Bool, pin: String?) async {
        phase = .working(reuse ? "Connecting — reusing saved token…" : "Connecting…")

        let opened: ConnectionManager.Opened
        do {
            opened = try await ConnectionManager.open(
                transport: transport,
                nfcAlertMessage: "Hold your YubiKey near the top of your phone."
            )
        } catch {
            phase = .failed("Could not connect: \(describe(error))")
            return
        }

        let session = opened.session
        do {
            try await run(session: session, reuse: reuse, pin: pin)
        } catch is StaleTokenError {
            try? store.clear()
            DeviceIdentityCache.clear()
            refreshSavedTokenState()
            phase = .failed(
                "The saved token was rejected (PIN changed or key was reset). "
                    + "Acquire a new token with your PIN."
            )
        } catch {
            phase = .failed(describe(error))
        }

        await opened.close()
    }

    private func run(session: CTAP2.Session, reuse: Bool, pin: String?) async throws {
        // Feature detection / firmware gate.
        //
        // Pre-release/engineering YubiKeys report a placeholder version (e.g. 0.0.x)
        // that would fail a naive `>= 5.8.0` comparison. We treat a 0.0.x build as a
        // pre-release that may carry the feature, and rely on the real feature
        // detection below (encIdentifier / encCredStoreState / PCMR) to gate support.
        let firmware = await session.version
        let isPreRelease = firmware.major == 0 && firmware.minor == 0
        guard isPreRelease || firmware >= Version("5.8.0")! else {
            phase = .unsupported(
                "Requires YubiKey firmware 5.8.0 or later (found \(firmware))."
            )
            return
        }

        let info = try await session.getInfo()
        guard info.encIdentifier != nil, info.encCredStoreState != nil else {
            phase = .unsupported(
                "This YubiKey does not expose the persistent device-identity fields "
                    + "(encIdentifier / encCredStoreState)."
            )
            return
        }
        guard try await CTAP2.CredentialManagement.isReadOnlySupported(by: session) else {
            phase = .unsupported(
                "This YubiKey does not support Persistent Credential Management Read-Only (PCMR)."
            )
            return
        }

        // Obtain the persistent token — from the Keychain (reuse) or via one PIN entry.
        let token: CTAP2.Token
        if reuse {
            guard let saved = try store.load() else {
                phase = .failed("No saved token found. Acquire one with your PIN first.")
                return
            }
            token = saved
        } else {
            guard let pin, !pin.isEmpty else {
                phase = .failed("Enter your FIDO2 PIN.")
                return
            }
            phase = .working("Acquiring persistent token (PPUAT)…")
            token = try await session.getPinUVToken(
                using: .pin(pin),
                permissions: [.persistentCredentialManagement]
            )
            try store.save(token)
            refreshSavedTokenState()
        }

        try await buildReport(session: session, info: info, token: token, reusedFromDisk: reuse)
    }

    private func buildReport(
        session: CTAP2.Session,
        info: CTAP2.GetInfo.Response,
        token: CTAP2.Token,
        reusedFromDisk: Bool
    ) async throws {
        phase = .working("Decrypting device identity…")
        // Non-optional: presence was verified in run(...).
        let deviceIdHex: String
        let credStateHex: String
        do {
            deviceIdHex = try info.encIdentifier!.decryptedData(using: token).hexString
            credStateHex = try info.encCredStoreState!.decryptedData(using: token).hexString
        } catch {
            // A stale token yields a decryption/auth failure on reuse.
            if reusedFromDisk { throw StaleTokenError() }
            throw error
        }

        // encCredStoreState cache-invalidation demo.
        let cached = DeviceIdentityCache.load(for: deviceIdHex)
        let cacheStatus: CacheStatus
        let relyingParties: [DeviceIdentityCache.RelyingParty]
        let existing: Int
        let remaining: Int

        if let cached, cached.credStoreStateHex == credStateHex {
            cacheStatus = .hit
            relyingParties = cached.relyingParties
            existing = cached.existingCredentialsCount
            remaining = cached.maxRemainingCredentialsCount
        } else {
            cacheStatus = (cached == nil) ? .firstRun : .miss
            let inventory = try await enumerate(session: session, token: token, reusedFromDisk: reusedFromDisk)
            relyingParties = inventory.relyingParties
            existing = inventory.existing
            remaining = inventory.remaining
            DeviceIdentityCache(
                deviceIdHex: deviceIdHex,
                credStoreStateHex: credStateHex,
                existingCredentialsCount: existing,
                maxRemainingCredentialsCount: remaining,
                relyingParties: relyingParties
            ).save()
        }

        // Read-only scope probe: a PCMR token must not be able to delete a credential.
        let scopeProbe = await runScopeProbe(session: session, token: token, relyingParties: relyingParties)

        refreshSavedTokenState()
        phase = .result(
            DeviceIdentityReport(
                reusedFromDisk: reusedFromDisk,
                firmware: (await session.version).description,
                deviceIdHex: deviceIdHex,
                credStoreStateHex: credStateHex,
                cacheStatus: cacheStatus,
                existingCredentialsCount: existing,
                maxRemainingCredentialsCount: remaining,
                relyingParties: relyingParties,
                scopeProbe: scopeProbe
            )
        )
    }

    private func enumerate(
        session: CTAP2.Session,
        token: CTAP2.Token,
        reusedFromDisk: Bool
    ) async throws -> (relyingParties: [DeviceIdentityCache.RelyingParty], existing: Int, remaining: Int) {
        phase = .working("Enumerating credentials…")
        let credMgmt: CTAP2.CredentialManagement
        let metadata: CTAP2.CredentialManagement.Metadata
        do {
            credMgmt = try await session.credentialManagement(token: token)
            metadata = try await credMgmt.getMetadata()
        } catch {
            if reusedFromDisk { throw StaleTokenError() }
            throw error
        }

        var built: [DeviceIdentityCache.RelyingParty] = []
        for rp in try await credMgmt.rps.enumerate() {
            var creds: [DeviceIdentityCache.Credential] = []
            for cred in try await credMgmt.credentials(for: rp.rpIdHash).enumerate() {
                creds.append(
                    DeviceIdentityCache.Credential(
                        credentialIdHex: cred.credentialId.id.hexString,
                        userName: cred.user.name,
                        userDisplayName: cred.user.displayName
                    )
                )
            }
            built.append(
                DeviceIdentityCache.RelyingParty(rpId: rp.rp.id, rpName: rp.rp.name, credentials: creds)
            )
        }

        return (built, Int(metadata.existingCredentialsCount), Int(metadata.maxRemainingCredentialsCount))
    }

    private func runScopeProbe(
        session: CTAP2.Session,
        token: CTAP2.Token,
        relyingParties: [DeviceIdentityCache.RelyingParty]
    ) async -> ScopeProbeResult {
        guard let hex = relyingParties.first?.credentials.first?.credentialIdHex,
            let idData = Data(hexString: hex)
        else {
            return .notRun("No discoverable credentials available to probe.")
        }
        do {
            let credMgmt = try await session.credentialManagement(token: token)
            // A PCMR token must reject this — the credential is NOT actually deleted.
            try await credMgmt.deleteCredential(WebAuthn.CredentialDescriptor(id: idData))
            return .unexpectedlyAllowed
        } catch {
            return .rejected(describe(error))
        }
    }

    private func describe(_ error: Error) -> String {
        if let custom = error as? CustomStringConvertible {
            return custom.description
        }
        return String(describing: error)
    }
}
