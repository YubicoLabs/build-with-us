// ARKGViewModel.swift
// State machine for the ARKG quickstart 4-step flow, one user action per step.
// Step A: register()    → MakeCredential → GeneratedKey
// Step B: deriveKeys()  → Offline ARKG derivation (5 derived keys; no YubiKey)
// Step C: sign(message:) → GetAssertion with one derived key
// Step D: verify()      → Offline ECDSA-P256 verify

import Foundation
import SwiftUI
import CryptoKit
import YubiKit
import FidoUI

@MainActor
final class ARKGViewModel: ObservableObject {

    enum State {
        case idle
        case inProgress
        case registered(
            credentialId: Data,
            generatedKey: CTAP2.Extension.PreviewSign.GeneratedKey
        )
        case keysReady(
            credentialId: Data,
            generatedKey: CTAP2.Extension.PreviewSign.GeneratedKey,
            derivedKeys: [DerivedKey]
        )
        case composeMessage(
            credentialId: Data,
            generatedKey: CTAP2.Extension.PreviewSign.GeneratedKey,
            derivedKey: DerivedKey
        )
        case signed(
            message: Data,
            signature: Data,
            derivedKey: DerivedKey
        )
        case verified(
            message: Data,
            signature: Data,
            isValid: Bool
        )
        case error(String)
    }

    struct DerivedKey: Identifiable {
        let id: Int
        let context: Data
        let publicKey: Data       // 65-byte uncompressed P-256 (share with verifiers)
        let arkgKeyHandle: Data   // ARKG ticket (send to YubiKey at sign time)
    }

    @Published var state: State = .idle

    private let fidoUI = FidoUI(allowedExtensions: .all)
    private let origin = try! WebAuthn.Origin("https://demo.yubico.com")
    private let rpId   = "demo.yubico.com"

    // MARK: - Step A: Register

    func register() async {
        state = .inProgress
        do {
            let challenge = randomBytes(32)
            let userId    = randomBytes(16)

            // YubiKey 5.8 firmware doesn't accept -65539 as the main credential
            // algorithm, so include standard fallbacks. previewSign generates the
            // ARKG key pair independently of whichever algorithm is chosen here.
            let options = WebAuthn.Registration.Options(
                challenge: challenge,
                rp:   .init(id: rpId, name: "ARKG Demo"),
                user: .init(id: userId, name: "arkg-demo"),
                userVerification: .discouraged,
                pubKeyCredParams: [.esp256SplitARKGPlaceholder, .esp256, .edDSA, .es256],
                extensions: .init(previewSign: .generateKey(algorithms: [.esp256SplitARKGPlaceholder]))
            )

            let regResponse = try await fidoUI.makeCredential(options, origin: origin, serviceName: "ARKG Demo")

            guard let genKey = regResponse.clientExtensionResults.previewSign?.generatedKey else {
                throw ARKGError.noGeneratedKey
            }

            state = .registered(credentialId: regResponse.credentialId, generatedKey: genKey)
        } catch {
            state = .error(error.localizedDescription)
        }
    }

    // MARK: - Step B: Derive (offline; YubiKey can be removed)

    func deriveKeys() {
        guard case let .registered(credentialId, genKey) = state else { return }
        do {
            let (pkBl, pkKem) = try ARKGCOSEKey.parse(genKey.publicKey)
            let derivedKeys = try (0..<5).map { i -> DerivedKey in
                let ikm     = randomBytes(32)
                let context = Data("arkg-quickstart-\(i)".utf8)
                let (pub, handle) = try ARKG.derivePublicKey(pkKem: pkKem, pkBl: pkBl, ikm: ikm, context: context)
                return DerivedKey(id: i, context: context, publicKey: pub, arkgKeyHandle: handle)
            }
            state = .keysReady(credentialId: credentialId, generatedKey: genKey, derivedKeys: derivedKeys)
        } catch {
            state = .error(error.localizedDescription)
        }
    }

    // MARK: - Key selection → compose message

    func selectKey(_ key: DerivedKey) {
        guard case let .keysReady(credentialId, genKey, _) = state else { return }
        state = .composeMessage(credentialId: credentialId, generatedKey: genKey, derivedKey: key)
    }

    // MARK: - Step C: Sign

    func sign(message: Data) async {
        guard case let .composeMessage(credentialId, generatedKey, derivedKey) = state else { return }
        state = .inProgress
        do {
            let additionalArgs = ARKG.buildAdditionalArgs(
                context: derivedKey.context,
                arkgKeyHandle: derivedKey.arkgKeyHandle
            )

            // The previewSign extension does NOT hash internally — `tbs` must be the
            // pre-computed SHA-256 digest. Verification with CryptoKit's
            // isValidSignature(_:for: Data) hashes the same way, so we keep `message`
            // raw in `.signed` and let CryptoKit re-hash on the verify side.
            let digest = Data(SHA256.hash(data: message))
            let challenge = randomBytes(32)
            let options = WebAuthn.Authentication.Options(
                challenge:        challenge,
                rpId:             rpId,
                allowCredentials: [.init(id: credentialId)],
                userVerification: .discouraged,
                extensions: .init(previewSign: .init(signByCredential: [
                    credentialId: .init(
                        keyHandle:      generatedKey.keyHandle,
                        tbs:            digest,
                        additionalArgs: additionalArgs
                    )
                ]))
            )

            let authResponse = try await fidoUI.getAssertion(options, origin: origin, serviceName: "ARKG Demo")

            guard let signature = authResponse.clientExtensionResults.previewSign?.signature else {
                throw ARKGError.noSignature
            }

            state = .signed(message: message, signature: signature, derivedKey: derivedKey)
        } catch {
            state = .error(error.localizedDescription)
        }
    }

    // MARK: - Step D: Verify (offline)

    func verify() {
        guard case let .signed(message, signature, derivedKey) = state else { return }
        do {
            // CryptoKit hashes `message` internally with SHA-256.
            let isValid = try ARKG.verifySignature(
                publicKey:    derivedKey.publicKey,
                message:      message,
                derSignature: signature
            )
            state = .verified(message: message, signature: signature, isValid: isValid)
        } catch {
            state = .error(error.localizedDescription)
        }
    }

    func reset() { state = .idle }
}

// MARK: - Helpers

private func randomBytes(_ count: Int) -> Data {
    Data((0..<count).map { _ in UInt8.random(in: 0...255) })
}
