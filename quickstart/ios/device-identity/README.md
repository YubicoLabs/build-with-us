# Device Identity Quickstart (iOS + macOS)

This is an iOS/macOS quickstart project demonstrating the new **Persistent PIN/UV Auth Token (PPUAT)** primitives in YubiKey 5.8 using [yubikit-swift](https://github.com/Yubico/yubikit-swift). This project is written in Swift and is similar to the .NET
`device-identity` quickstart but adds **true on‑disk token persistence** so passkeys can be recognized across app launches with no repeated PIN prompt.

Before firmware 5.8, hardware security keys were invisible to passkey autofill — a platform had to ask for the PIN on every connection. PPUAT changes that: The user enters their PIN **once**, and from then
on the platform can silently recognize the key, detect whether its credentials changed, and list
them without another PIN prompt.

## This iOS/macOS quickstart demonstrates

1. **Feature detection** — Requires YubiKey 5.8 with Persistent Credential Management Read‑Only (PCMR) support; unsupported keys (prioer to 5.8) are handled with a clear message.
2. **Acquire PPUAT** — one PIN entry via
   `getPinUVToken(using:.pin, permissions:[.persistentCredentialManagement])`.
3. **On‑disk persistence** — the raw token is saved to the **iOS/macOS Keychain**
   (`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`) and reloaded on the next launch.
4. **Cross‑session reuse (no PIN)** — the saved token is rebuilt with
   `CTAP2.Token(rawValue:protocolVersion:)` and reused.
5. **`encIdentifier`** — decrypted to a stable 16‑byte device ID.
6. **`encCredStoreState`** — decrypted and used for cache invalidation
   (`unchanged → skip enumeration`, `changed → re‑enumerate`).
7. **Enumeration** — relying parties, credentials, and metadata counts via the PPUAT.
8. **Read‑only scope probe** — attempting `deleteCredential` with the PCMR token is rejected by the key, proving the token cannot modify credentials (e.g. the credential is **not** deleted).

## Requirements

- Xcode 16+ (Swift 6), iOS 17+ / macOS 14+.
- A YubiKey with **firmware 5.8.0 or later**, a **FIDO2 PIN set**, and ideally ≥ 1 discoverable
  credential (passkey) so the inventory and scope probe have something to show.
- The [yubikit-swift](https://github.com/Yubico/yubikit-swift) SDK checked out at
  `../../yubico/5.8/early-access-program/quickstart/ios/yubikit-swift` (the project references it as
  a local Swift package — adjust the path in the project's *Package Dependencies* if your layout
  differs).

## SDK requirement — `CTAP2.Token` persistence API

Using a modified local copy of the Swift SDK to expose the token bytes. This quickstart depends on two functions of `CTAP2.Token` (in `FIDO/CTAP/ClientPIN/Types.swift`):

```swift
public var rawValue: Data { get }                                        // raw token bytes
public init?(rawValue: Data, protocolVersion: ClientPin.ProtocolVersion) // reconstruct from bytes
```

These are similar to the .NET SDK's `Fido2Session.AuthTokenPersistent` getter and the
`Fido2Session(yubiKey, persistentPinUvAuthToken:)` constructor.

## Project layout

```
DeviceIdentityQuickstart/
├── DeviceIdentityQuickstartApp.swift   @main
├── ContentView.swift                   state-driven main
├── DeviceIdentityViewModel.swift       flow state machine
├── Hex.swift                           Data <-> hex helpers
├── Connection/ConnectionManager.swift  direct NFC / wired / HID connections
├── Persistence/
│   ├── PpuatStore.swift                token store protocol
│   ├── KeychainPpuatStore.swift        Keychain-backed token store
│   └── DeviceIdentityCache.swift       non-secret cache (device ID, state, creds)
└── Views/                              Home, PINEntry, InProgress, DeviceInfo,
                                        CredentialList, ScopeProbe, Result
```

## Get Started

1. Open `DeviceIdentityQuickstart.xcodeproj`, select the **DeviceIdentity_iOS** or
   **DeviceIdentity_macOS** scheme, and set your signing team.
2. Launch, choose a transport (iOS: NFC or USB/Lightning; macOS: USB HID).
3. **Acquire PPUAT** and enter your PIN once: device ID, credential store state, and inventory appear, and the token is saved.
4. Relaunch the app and tap **Reuse Saved Token**: the same device is confirmed and credentials are shown without any pin prompt. TODO: The scope probe should show the delete request being rejected.

This quickstart mirrors the .NET PPUAT "Session 1 → Session 2" flow, but Session 2 survives a full app restart because our token is persisted in the iOS/macOS Keychain.

## Notes

- The PPUAT is sensitive key material. It is stored only in the Keychain, which is non‑syncing accessibility level therefore will never be in plaintext, `UserDefaults`, or exist in iCloud Keychain backups.
- NOTE: A PPUAT is invalidated when the PIN changes or the FIDO2 app is reset on the YubiKey. When the key rejects a reused token, the app clears it from the Keychain and prompts for the PIN again.
- The scope probe uses a read‑only (PCMR) token, so the attempted delete is rejected by the key and no credential is removed.
