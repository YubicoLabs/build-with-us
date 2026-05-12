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
├── prf-hmac-secret/          # hmac-secret-mc (PRF)
├── third-party-payments/     # thirdPartyPayment, credProtect, and credBlob
├── yubikey-management/       # GetInfo, discovery, pin
├── WebAuthnInterceptor/       # iOS/macOS app with embedded WKWebView

```

## Quickstarts
A few of the quickstarts below share the same sample project (WebAuthnInterceptorSample) that is an iOS/macOS app with an embedded WKWebView that allows you to interact with some of the newer features via the web and the YubiKey 5.8 like preview signing, prf, and others.

[README](WebAuthnInterceptorSample/README.md) for the WebAuthnInterceptorSample

### PreviewSign (Signing preview)

The `previewSign` extension is a [proposed WebAuthn extension](https://yubicolabs.github.io/webauthn-sign-extension/4/#sctn-sign-extension) that supports hardware-backed ECDSA P-256 signing over application-defined data.

- `previewSign`

[README](previewSign/README.md) &#183; [Code](WebAuthnInterceptorSample/WebAuthnInterceptorSample/WebAuthnHandler.swift)

---

### prf-hmac-secret (hmac-secret-mc)

#### <COMING SOON>(COMING SOON)
---

### third-party-payments

#### <COMING SOON>(COMING SOON)
---

### yubikey-management (getInfo mostly and pin mgmnt)

#### <COMING SOON>(COMING SOON)
---