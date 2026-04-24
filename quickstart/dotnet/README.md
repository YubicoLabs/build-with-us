# YubiKey 5.8 FIDO2 Quickstarts for .NET

Code examples for FIDO2 features introduced in YubiKey firmware 5.8, using the [Yubico .NET SDK](https://github.com/Yubico/Yubico.NET.SDK).

## Prerequisites

| Requirement | Details |
|---|---|
| .NET | 8.0 or later ([download](https://dotnet.microsoft.com/download)) |
| YubiKey | Firmware 5.8 or later. The runner checks this automatically and will tell you if your key is too old. |
| FIDO2 PIN | A PIN must be set on the key. You can do this in [YubiKey Manager](https://www.yubico.com/support/download/yubikey-manager/) under **Applications → FIDO2 → Set PIN**. |
| Admin shell | Run from an elevated or privileged terminal for USB device access. |
| Exclusive access | Close YubiKey Manager, browsers and smart card services before running. |

## Examples

### GetInfo discovery

Reads `AuthenticatorInfo` fields supported in firmware 5.8. No credentials are created and no PIN or touch is needed.

- `maxPINLength`
- `pinComplexityPolicy`
- `pinComplexityPolicyURL`
- `uvCountSinceLastPinEntry`
- `attestationFormats`
- `transportsForReset`
- `longTouchForReset`

[README](authenticator-config/README.md) &#183; [Code](authenticator-config/AuthenticatorConfigDemo.cs)

---

### Persistent token and device identity

Implements PPUAT acquisition with PCMR permission, `encIdentifier` decryption, `encCredStoreState` decryption and cross-session token reuse. These are the primitives that support conditional mediation for hardware security keys.

- Persistent PIN UV Auth Token (PPUAT)
- Persistent Credential Management Read Only (PCMR)
- `encIdentifier`
- `encCredStoreState`

[README](device-identity/README.md) &#183; [Code](device-identity/DeviceIdentityDemo.cs)

---

### hmac-secret-mc (PRF)

Derives a 32-byte secret during `MakeCredential` using the `hmac-secret-mc` extension. On firmware 5.8 this completes in a single user interaction instead of requiring a separate `GetAssertion` call.

- `hmac-secret-mc`

[README](hmac-secret-prf/README.md) &#183; [Code](hmac-secret-prf/HmacSecretDemo.cs)

---

### Third-party payment

Creates a payment credential with `thirdPartyPayment`, locks it down with `credProtect` so every use requires PIN or biometric and stores a card label on the key with `credBlob`. Then runs a simulated merchant checkout.

- `thirdPartyPayment`
- `credBlob`
- `credProtect`

[README](third-party-payment/README.md) &#183; [Code](third-party-payment/ThirdPartyPaymentDemo.cs)

---

### Signing preview

The `previewSign` extension is a [proposed WebAuthn extension](https://yubicolabs.github.io/webauthn-sign-extension/4/#sctn-sign-extension) that supports hardware-backed ECDSA P-256 signing over application-defined data. It uses a separate credential type that cannot be used for authentication. Uses an early-access SDK fork with ARKG key derivation support.

- `previewSign`

[README](preview-sign/README.md) &#183; [Code](preview-sign/PreviewSignDemo.cs)

---

## Additional firmware 5.8 capabilities

The following are part of firmware 5.8 but do not have standalone examples:

| Capability | Notes |
|---|---|
| FIDO over Smart Card (CCID) | CTAP commands transported over ISO 7816 / USB CCID. Handled at the platform layer; the Yubico .NET SDK selects the CCID transport automatically when available. No application code changes required. |
| PIN Protocol 1 signature length enforcement | Firmware 5.8 rejects HMAC signatures longer than 16 bytes during PIN Protocol 1 negotiation. Previous firmware truncated and accepted them. This is a spec-alignment change with no SDK API surface. |

## Usage

### Interactive runner

The runner presents a menu, validates that the connected YubiKey meets the firmware 5.8 minimum and dispatches the selected example.

```bash
git clone https://github.com/YubicoLabs/yubikey-dotnet-quickstarts.git
cd yubikey-dotnet-quickstarts
dotnet run
```

### Single example

Each subdirectory is a standalone console application:

```bash
cd device-identity
dotnet run
```

Connect a YubiKey before running. Examples that require a FIDO2 PIN will prompt for it.

## Web testing

Wondering how these features behave through WebAuthn in the Web? See the [browser feature guide](../web/README.md).

## SDK reference

These examples reference [Yubico.YubiKey](https://www.nuget.org/packages/Yubico.YubiKey) v1.15.1.

API documentation: [docs.yubico.com/yesdk](https://docs.yubico.com/yesdk/)