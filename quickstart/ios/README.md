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
├── ARKGPreviewSign/         # The previewSign macOS/iOS demo with ARKG key generation
├── device-identity/         # Enhance UX using PPUAT (Persistent PIN UV Auth Token)
├── third-party-payments/    # thirdPartyPayment, credProtect, and credBlob
├── yubikey-management/      # GetInfo, discovery, pin
├── WebAuthnInterceptorSample/     # iOS/macOS app with embedded WKWebView and WebAuthn interceptor

```

## Quickstarts
A few of the quickstarts below share the same sample project (`WebAuthnInterceptorSample`) that is an iOS/macOS app with an embedded WKWebView that allows you to interact with some of the newer features via the web and the YubiKey 5.8 like preview signing, prf, and others.

WebAuthnInterceptorSample [README](WebAuthnInterceptorSample/README.md)

---

### PreviewSign (Browswer-based Signing Extension Preview)
NOTE: This quickstart is using the WebAuthnInterceptorSample to demonstrate the preview signing feature through a web browser without installing a Safari Web Extension. 

- `previewSign`

[README](previewSign/README.md) &#183; [Code](WebAuthnInterceptorSample/WebAuthnInterceptorSample/WebAuthnHandler.swift)


---

### ARKGPreviewSign (Native macOS/iOS App)

ARKGPreviewSign is a standalone native macOS/iOS app that demonstrates The `previewSign` extension with ARKG key generaion and hardware-backed ECDSA P-256 signing.

- `ARKGPreviewSign`

[README](ARKGPreviewSign/README.md)

---

### device-identity (PPUAT - Persistent PIN UV Auth Token)

Implements PPUAT acquisition with PCMR permission, `encIdentifier` decryption, `encCredStoreState` decryption and cross-session token reuse. These are the primitives that support conditional mediation for hardware security keys.

- Persistent PIN UV Auth Token (PPUAT)
- Persistent Credential Management Read Only (PCMR)
- `encIdentifier`
- `encCredStoreState`

[README](device-identity/README.md)

---

### third-party-payments
Creates a payment credential with thirdPartyPayment, locks it down with credProtect so every use requires PIN or biometric and stores a card label on the key with credBlob. Then runs a simulated merchant checkout.

- `thirdPartyPayment`
- `credBlob`
- `credProtect`
#### README <COMING SOON>(COMING SOON)
---

### yubikey-management (getInfo mostly and pin management)

- #### FIDO over CCID 
    CTAP commands transported over ISO 7816 / USB CCID Handled at the platform layer; the Yubico iOS Swift SDK utilizes the CCID transport by default when available.

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
