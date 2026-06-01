# ARKG Quickstart for iOS and macOS

An iOS/macOS demo app that demonstrates the complete four-step **WebAuthn previewSign + ARKG** flow using a YubiKey 5.8 and the [YubiKit Swift SDK](https://github.com/Yubico/yubikit-swift/). 

Step A - Register and generate the ARKG root key using the preview signing extension as part of the YubiKey 5.8

Step B - Derive N P-256 signing public keys OFFLINE based on the ARKG root key

Step C - Have the YubiKey sign a message using ONE of N P-256 derived keys

Step D - Verify the signature; Confirm the signature is valid against the derived public key

---

## SDK | Framework layers

Each function call in this app is sourced from one of four places:

| Tag | Source |
|-----|--------|
| **[FidoUI]** | `FidoUI` package bundled with the YubiKit Swift SDK. A high-level ceremony wrapper (transport, PIN prompts, UI panels). Found in `../yubikit-swift/Samples/WebAuthnInterceptorSample/FidoUI` |
| **[YubiKit-Swift SDK]** | `YubiKit` package in `../yubikit-swift` — Used for CTAP2, WebAuthn types, and extension models in Steps A (makeCredential) and C (getAssertion) ONLY |
| **[CryptoKit]** | Apple [`CryptoKit` framework](https://developer.apple.com/documentation/cryptokit/) — Used for P-256, SHA-256, HKDF, and HMAC cryptographic operations |
| **[App]** | Custom code written for this quickstart — ARKG math, CBOR parsing, EC point addition |

---

## Prerequisites

- Physical YubiKey 5.8 (NFC or USB-C)
- The **patched** YubiKit SDK at `../yubikit-swift` (see [SDK patches](#sdk-patches) below)
- Xcode 15+, iOS 17+ device or macOS 14+ Mac

---

## Step A: Register and generate the ARKG root key (YubiKit-Swift)

**Goal:** Create a WebAuthn credential on the YubiKey and receive the ARKG root key pair (`GeneratedKey`) from the `previewSign` extension.

1. Build registration options 

    `ARKGViewModel.generateKey()` [[CODE](https://github.com/dmennis/ARKGQuickstart/blob/6ec4de1273dc72ad51a80b3db9c356c2227120ff/ARKGQuickstart/ARKGViewModel.swift#L54)]

2. Run the registration ceremony 

    ```swift
    // [FidoUI]
    let regResponse = try await fidoUI.makeCredential(
        options, origin: origin, serviceName: "ARKG Demo"
    )
    ```

3. Extract results from the response

    ```swift
    let credentialId = regResponse.credentialId // [YubiKit] public Data field

    // [YubiKit] CTAP2.Extension.PreviewSign.GeneratedKey
    guard let genKey = regResponse.clientExtensionResults.previewSign?.generatedKey else {
        throw ARKGError.noGeneratedKey
    }
    ```

**`GeneratedKey` (genKey) fields** — **[YubiKit-Swift]**:

| Field | Type | Description |
|-------|------|-------------|
| `keyHandle` | `Data` | Opaque ticket — returned by the YubiKey at sign time |
| `publicKey` | `Data` | CBOR-encoded COSE key (`kty=-65537`, `alg=-65700`) containing `pkBl` and `pkKem` |
| `algorithm` | `COSE.Algorithm` | Algorithm chosen by the YubiKey (`-65700` = ARKG_P256_ESP256) |
| `attestationObject` | `Data` | WebAuthn attestation object for the generated key |

> **The `previewSign` extension generates the ARKG key pair independently of whichever main algorithm is chosen from pubKeyCredParams.

---

## Step B: Derive public keys offline

**Goal:** Produce N unique P-256 signing keys from the ARKG root — No YubiKey contact needed.

**NOTE** ARKG is a key generation scheme where the authenticator (YubiKey) delegates generation of public keys to an external party (in this case, our demo app), but without giving access to the corresponding private keys. 

1. Parse the ARKG root key pair out of `GeneratedKey.publicKey` [[CODE](https://github.com/dmennis/ARKGQuickstart/blob/6ec4de1273dc72ad51a80b3db9c356c2227120ff/ARKGQuickstart/ARKGViewModel.swift#L72)]

    ***NOTE:*** `GeneratedKey.publicKey` is a CBOR map with a custom key type (`kty = -65537`) that neither CryptoKit nor YubiKit's standard COSE parser handles. `ARKGCOSEKey.parse` decodes it manually using `MiniCBOR` **[App]** and returns two 65-byte uncompressed P-256 points (`04 || x || y`).

    COSE key structure:

    | CBOR key | Value | Field |
    |----------|-------|-------|
    | `1` (kty) | `-65537` | ARKG split-EC key type |
    | `3` (alg) | `-65700` | ARKG_P256_ESP256 |
    | `-1` | EC2 sub-map | `pkBl` — blinding public key |
    | `-2` | EC2 sub-map | `pkKem` — KEM public key |

2. Derive N keys (5 in this quickstart) `ARKG.derivePublicKey` [[CODE](https://github.com/dmennis/ARKGQuickstart/blob/6ec4de1273dc72ad51a80b3db9c356c2227120ff/ARKGQuickstart/ARKGViewModel.swift#L73)]

    ***NOTE:*** `ARKG.derivePublicKey` Implements the ARKG-P256 offline derivation algorithm:

    | Internal function | Tag | What it does |
    |-------------------|-----|--------------|
    | `kemEncaps(pkKem:ikm:ctxKem:)` | **[App]** | Encapsulates a shared secret using ECDH + HMAC-KEM |
    | `subKemDeriveKeyPair(ikm:)` | **[App]** | Derives an ephemeral EC key pair from `ikm` via `hashToField` |
    | `p256ECDH(privateScalar:publicPoint:)` | **[App]** wraps **[CryptoKit]** | `P256.KeyAgreement` shared secret (x-coordinate) |
    | `hmacSha256(key:message:)` | **[App]** wraps **[CryptoKit]** | `HMAC<SHA256>.authenticationCode` |
    | `hkdfSha256(ikm:info:length:)` | **[App]** wraps **[CryptoKit]** | `HKDF<SHA256>.deriveKey` with null salt (32 zero bytes) |
    | `blPrf(ikmTau:ctxBl:)` | **[App]** | Derives scalar `τ` via `hashToField` |
    | `hashToField(msg:count:dst:)` | **[App]** | RFC 9380: hashes to P-256 scalar(s) mod `n` |
    | `expandMessageXmd(msg:lenInBytes:dst:)` | **[App]** wraps **[CryptoKit]** | RFC 9380 §5.4.1 `expand_message_xmd` using `SHA256.hash` |
    | `p256ModN48(_:)` | **[App]** | Reduces a 48-byte big-endian value mod P-256 group order `n` |
    | `p256ScalarMulG(scalar:)` | **[App]** wraps **[CryptoKit]** | `P256.KeyAgreement.PrivateKey(rawRepresentation:).publicKey.x963Representation` |
    | `p256Add(_:_:)` | **[App]** | EC point addition in affine coordinates (`pkBl + τ·G`) — not available in CryptoKit |

    The final derived public key is `pkBl + τ·G` (EC point addition). `arkgKeyHandle` is the ephemeral public key bytes plus a truncated HMAC, which the YubiKey uses to reproduce `τ` at sign time.

---

## Step C: Sign with a derived key (YubiKit-Swift) [[CODE](https://github.com/dmennis/ARKGQuickstart/blob/6ec4de1273dc72ad51a80b3db9c356c2227120ff/ARKGQuickstart/ARKGViewModel.swift#L88)]

**Goal:** Ask the YubiKey to sign a message using ONE specific derived key.

```swift
// ARKGViewModel.sign(derivedKey:credentialId:generatedKey:)

// 1. Encode the ARKG derivation parameters into the additionalArgs CBOR map [App]
let additionalArgs = ARKG.buildAdditionalArgs(           
    context:       derivedKey.context,
    arkgKeyHandle: derivedKey.arkgKeyHandle
)
// MiniCBOR.encode() is custom [App] code — YubiKit's CBOR types are internal

// 2. Build authentication options [YubiKit-Swift]
let options = WebAuthn.Authentication.Options(          
    challenge:        randomBytes(32),
    rpId:             rpId,
    allowCredentials: [.init(id: credentialId)],
    userVerification: .discouraged,
    extensions: .init(
        previewSign: .init(signByCredential: [           // [YubiKit] previewSign extension input
            credentialId: .init(
                keyHandle:      generatedKey.keyHandle,           // [YubiKit] ticket from Step A
                tbs:            Data(SHA256.hash(data: message)), // [CryptoKit] SHA-256 digest of the message
                additionalArgs: additionalArgs                    // [App] ARKG derivation params
            )
        ])
    )
)

// 3. Run the assertion ceremony
let authResponse = try await fidoUI.getAssertion(        // [FidoUI]
    options, origin: origin, serviceName: "ARKG Demo"
)

// 4. Extract the signature
let signature = authResponse.clientExtensionResults     // [YubiKit]
    .previewSign!.signature                             // [YubiKit] DER-encoded ECDSA-P256 signature
```

> **`tbs` must be the SHA-256 digest of the message — the YubiKey does NOT hash internally.** ECDSA on the YubiKey signs the `tbs` bytes directly. The app pre-hashes with SHA-256 and CryptoKit's `isValidSignature(_:for:)` does the same when verifying, so the two digests match.

---

## Step D: Verify the signature offline [[CODE](https://github.com/dmennis/ARKGQuickstart/blob/6ec4de1273dc72ad51a80b3db9c356c2227120ff/ARKGQuickstart/ARKGViewModel.swift#L121)]

**Goal:** Confirm the signature is valid against the derived public key; No YubiKey needed.

```swift
// ARKGViewModel.sign() continued

let verified = try ARKG.verifySignature(                 // [App]
    publicKey:    derivedKey.publicKey,                  // 65-byte uncompressed P-256 from Step B
    message:      message,                               // original bytes ("Hello World")
    derSignature: signature                              // DER-encoded signature from Step C
)
```

***`ARKG.verifySignature` wraps [CryptoKit]***

```swift
let pubKey = try P256.Signing.PublicKey(                 // [CryptoKit]
    x963Representation: publicKey
)
let sig = try P256.Signing.ECDSASignature(               // [CryptoKit]
    derRepresentation: derSignature
)
return pubKey.isValidSignature(sig, for: message)        // [CryptoKit] hashes message with SHA-256 internally
```

Pass the original message bytes. `isValidSignature(_:for:)` with a `Data` argument hashes internally with SHA-256 — producing the exact same digest the app sent as `tbs` to the YubiKey in Step C, which the YubiKey signed directly with ECDSA.

---

## Getting Started
1. Clone this repo
2. cd ~/ARKGQuickstart
3. Open project in Xcode: 
    > xed .
4. Build and run on physical macOS or iOS device
---
## Demo Videos

<table>
  <tr>
    <th align="center">macOS Demo</th>
    <th align="center">iOS Demo</th>
  </tr>
  <tr>
    <td>
      <video src="./assets/ARKG-macOS-Demo/ARKG-macOS-Demo.mp4" width="75%" controls></video>
    </td>
    <td>
      <video src="./assets/ARKG-iPhone-Demo/ARKG-iPhone-Demo.m4v" width="75%" controls></video>
    </td>
  </tr>
</table>

---
<details>

<summary>YubiKit-Swift SDK patches</summary>

### Updates to YubiKit-Swift SDK/FidoUI to support this ARKGQuickstart Demo

The `FidoUI` package originally hard-codes `allowedExtensions: .standard` when it constructs `WebAuthn.Client`. The `.standard` set explicitly excludes `.previewSign` (marked experimental). Without the patches below, any `previewSign` inputs in `Registration.Options` or `Authentication.Options` are silently dropped before reaching the CTAP layer.

Two files in `../yubikit-swift` were modified:

### `FidoUI/Sources/FidoUI/FidoUI.swift`

Added `allowedExtensions` to `public init` and captured it in the transport factory closure.

```swift
// Before
public init(isPublicSuffix: @escaping WebAuthn.PublicSuffixChecker = { _ in false }) {
    self.transportFactory = { origin in
        TransportController(origin: origin, isPublicSuffix: isPublicSuffix)
    }
}

// After
public init(
    allowedExtensions: Set<WebAuthn.Extension.Identifier> = .standard,
    isPublicSuffix: @escaping WebAuthn.PublicSuffixChecker = { _ in false }
) {
    self.transportFactory = { origin in
        TransportController(origin: origin,
                            allowedExtensions: allowedExtensions,
                            isPublicSuffix: isPublicSuffix)
    }
}
```

### `FidoUI/Sources/FidoUI/Transport/TransportController.swift`

Added `allowedExtensions` as a stored property and passed it to both `WebAuthn.Client` construction sites (NFC path on iOS and wired/HID path on macOS).

```swift
// Added stored property
private let allowedExtensions: Set<WebAuthn.Extension.Identifier>

// Updated init
init(origin: WebAuthn.Origin,
     allowedExtensions: Set<WebAuthn.Extension.Identifier> = .standard,
     isPublicSuffix: @escaping WebAuthn.PublicSuffixChecker) {
    self.allowedExtensions = allowedExtensions
    ...
}

// Both WebAuthn.Client(...) calls now include:
let client = WebAuthn.Client(
    session:           ctap,
    origin:            origin,
    allowedExtensions: allowedExtensions,   // ← added
    isPublicSuffix:    isPublicSuffix
)
```

The default value `.standard` preserves original behavior for all existing callers. This app passes `.all`:

```swift
private let fidoUI = FidoUI(allowedExtensions: .all)
```

</details>


