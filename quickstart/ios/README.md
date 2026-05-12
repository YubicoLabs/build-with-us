# YubiKey 5.8 Quickstarts for iOS/macOS

iOS/macOS Code/project samples demonstrating features of the YubiKey 5.8, using the [Yubico Swift SDK](https://github.com/Yubico/yubikit-swift/).

## 🛠 Prerequisites

| Requirement | Details |
|---|---|
| Yubico Swift SDK | Release 1.3.0 or later ([download](https://github.com/Yubico/yubikit-swift/releases/tag/v1.3.0)) |
| YubiKey | Firmware 5.8 or later. |
| FIDO2 PIN | A PIN must be set on the YubiKey to run most of these examples. You can do this in the YubiKey Authenticator, available on the [macOS App Store](https://apps.apple.com/us/app/yubico-authenticator/id1497506650?mt=12) |
| Xcode ([latest](https://apps.apple.com/us/app/xcode/id497799835?mt=12)) | For building and running the quickstart examples/demos on a realy iOS device or mac running macOS |

---

## 🏗 Projects/Samples/Demos 

```text
iOS/
├── previewSign/             # The previewSign extension proposed WebAuthn extension
├── device-identity/          # PPUAT (Persistent PIN UV Auth Token)
├── prf-hmac-secret/          # hmac-secret-mc (PRF)
├── third-party-payments/     # thirdPartyPayment, credProtect, and credBlob
├── yubikey-management/       # GetInfo, discovery, pin
├── WebAuthnInterceptor/       # iOS/macOS app with embedded WKWebView and WebAuthn interceptor

```

## Quickstarts
A few of the quickstarts below share the same sample project (`WebAuthnInterceptorSample`) that is an iOS/macOS app with an embedded WKWebView that allows you to interact with some of the newer features via the web and the YubiKey 5.8 like preview signing, prf, and others.

WebAuthnInterceptorSample [README](WebAuthnInterceptorSample/README.md)

---

### PreviewSign (Signing preview)

The `previewSign` extension is a [proposed WebAuthn extension](https://yubicolabs.github.io/webauthn-sign-extension/4/#sctn-sign-extension) that supports hardware-backed ECDSA P-256 signing over application-defined data.

- `previewSign`

[README](previewSign/README.md) &#183; [Code](WebAuthnInterceptorSample/WebAuthnInterceptorSample/WebAuthnHandler.swift)

---

### device-identity (PPUAT - Persistent PIN UV Auth Token)

Implements PPUAT acquisition with PCMR permission, `encIdentifier` decryption, `encCredStoreState` decryption and cross-session token reuse. These are the primitives that support conditional mediation for hardware security keys.

- Persistent PIN UV Auth Token (PPUAT)
- Persistent Credential Management Read Only (PCMR)
- `encIdentifier`
- `encCredStoreState`

#### README <COMING SOON>(COMING SOON)
---

### prf-hmac-secret (hmac-secret-mc)
Derives a 32-byte secret during MakeCredential using the hmac-secret-mc extension. On firmware 5.8 this completes in a single user interaction instead of requiring a separate GetAssertion call.

- `hmac-secret-mc`
#### README <COMING SOON>(COMING SOON)
---

### third-party-payments
Creates a payment credential with thirdPartyPayment, locks it down with credProtect so every use requires PIN or biometric and stores a card label on the key with credBlob. Then runs a simulated merchant checkout.

- `thirdPartyPayment`
- `credBlob`
- `credProtect`
#### README <COMING SOON>(COMING SOON)
---

### yubikey-management (getInfo mostly and pin mgmnt)

- #### FIDO over CCID 
    CTAP commands transported over ISO 7816 / USB CCID Handled at the platform layer; the Yubico iOS Swift SDK utilizes the CCID transport automatically when available.

- #### GetInfo discovery

    Reads AuthenticatorInfo fields supported in firmware 5.8. No credentials are created and no PIN or touch is needed.
    - `maxPINLength`
    - `pinComplexityPolicy`
    - `pinComplexityPolicyURL`
    - `uvCountSinceLastPinEntry`
    - `attestationFormats`
    - `transportsForReset`
    - `longTouchForReset`

#### README <COMING SOON>(COMING SOON)
---