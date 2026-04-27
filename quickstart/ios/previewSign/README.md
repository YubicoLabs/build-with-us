# Signing Preview

The `previewSign` FIDO2 extension lets a YubiKey sign raw data without including client or authenticator metadata in the signature. Standard FIDO2 signatures wrap your data in `authenticatorData || SHA-256(clientData)`, which means they only work with FIDO2 verifiers. Signatures from previewSign are made over the given input unaltered, so they work with any protocol that accepts ECDSA P-256 signatures: PDF signing (PAdES), JWT/DPoP tokens, S/MIME email, C2PA content integrity, SPIFFE workload identities, or any other standard.

The extension uses ARKG (Asynchronous Remote Key Generation) to generate unlimited unique signing keys offline from a single YubiKey credential. Each derived key is cryptographically unlinkable: a verifier cannot tell that two derived keys came from the same device. You only need to touch the YubiKey when actually producing a signature.

Requires YubiKey firmware 5.8+. See the main [project README](../README.md) for setup instructions.

> **Early Access Only.** This quickstart requires Yubico Swift SDK release/1.3.0 specifically for the Early Access Program. The previewSign extension, algorithm ID (-65539), and all SDK APIs shown here are not final and may change before general availability. Do not use this in production.

## When to use this

previewSign is for signing arbitrary data with hardware-backed keys. It is not for authentication.

Real world examples from the spec and team discussions:

- **Digital identity (EUDI).** Present attributes like "age over 18" or "nationality" to different services, each signed with a unique key. The services cannot cross-reference their logs to determine the same person made both presentations.
- **Document signing.** Sign contracts, PDFs, or legal documents. The signature is standard ECDSA P-256, compatible with existing verification tools.
- **Software signing.** Publish a derived public key as a release signing key. When you need to sign a release, touch the YubiKey to produce the signature.
- **Workload identity.** Issue hardware-bound signing keys for SPIFFE or OAuth DPoP without exposing private key material to the host.

ARKG makes it practically free to generate public keys. You only pay the user interaction cost (touch) when you actually use the associated private key. This is important for short-lived credentials like EUDI attribute certificates that expire after a week: you might generate 20 keys but only end up using a few of them.

## How ARKG works

When you create a credential with previewSign, the YubiKey generates two key pairs internally. It gives you the public halves:

- **pkBl (blinding public key):** Used to derive new unique public keys offline. Each derivation produces a new ECDSA P-256 public key that looks completely unrelated to pkBl or any other derived key.
- **pkKem (KEM public key):** Used to produce a "ticket" (the ARKG key handle) during derivation. The ticket is what you send back to the YubiKey when signing so it can recreate the matching private key on the fly.

The private halves (`skBl`, `skKem`) never leave the YubiKey.

Think of `pkBl` and `pkKem` as two halves of the same root key: one that makes public keys, one that makes tickets. The SDK wraps both inside a `PreviewSignGeneratedKey` object so you don't need to handle them separately.

Algorithm: `ARKG_P256_ESP256` (-65539).

## Flow

```
Step A: Generate Key (touch YubiKey, one time per RP)
  MakeCredential with the previewSign extension.
  YubiKey returns pkBl + pkKem and a credential ID.
  In a real app, save these to your database. You need them to derive new
  keys and sign later without touching the YubiKey again for registration.
  In this demo they stay in memory since the whole flow runs in one session.

Step B: Derive Public Key (offline, no YubiKey, unlimited times)
  generatedKey.DerivePublicKey(ikm, ctx)
  You provide:
    - ikm: 32 random bytes you generate. The randomness that makes each
      derived key unique and unlinkable. Different ikm = different key.
    - ctx: a label string scoping the key to a purpose (e.g. "contract-123").
  Already stored in generatedKey from Step A: pkBl and pkKem.
  The SDK reads them internally so you never pass them in yourself.
  Output:
    - Derived public key: a unique P-256 public key. Share it with verifiers.
    - ARKG key handle (ticket): save it. Send it to the YubiKey when signing.
  You can discard ikm after this call. Different ikm or ctx = different unlinkable key.

Step C: Sign (touch YubiKey, one touch per signature)
  getAssertionParams.AddPreviewSignByCredentialExtension(derivedKey, message)
  You pass derivedKey and the message.
  Carried over from Step A inside derivedKey: DeviceKeyHandle (the credential ID).
  Carried over from Step B inside derivedKey: ArkgKeyHandle (the ticket).
  The SDK reads both from derivedKey and hashes the message internally (SHA-256).
  The YubiKey reads the ticket, recreates the matching private key, signs
  the hash, returns a DER-encoded ECDSA signature, then discards the private key.

Step D: Verify (offline, no YubiKey)
  derivedKey.VerifySignature(originalMessage, signature)
  You pass the original message and the signature.
  Carried over from Step B inside derivedKey: PublicKey (the derived public key).
  The SDK reads it internally and runs standard ECDSA verify.
  Anyone with the derived public key can verify. No secrets, no YubiKey needed.
```

### What to store

After Step A (once):
- `credentialId` to identify the credential on the YubiKey
- `pkBl` + `pkKem` (inside `PreviewSignGeneratedKey`) to derive future keys

After Step B (per derived key):
- Derived public key, to share with verifiers
- ARKG key handle (ticket), to request signatures from the YubiKey
- Context label, for your own bookkeeping

You can discard the random bytes (`ikm`) after derivation. They are baked into the ticket.

### What the verifier receives

Three things:
- The original document
- The DER-encoded ECDSA signature
- The derived public key

The derived public key is a standard P-256 key. The verifier does not need to know about ARKG, previewSign, or the YubiKey. They just run standard ECDSA verification.

## About PreviewSign Sample

This quickstart uses the [WebAuthnInterceptorSample](../WebAuthnInterceptorSample/README.md) application with an embedded WKWebView and integrated Yubico Swift SDK release/1.3.0.
The WebAuthnInterceptorSample can intercept WebAuthn calls and interacts directly with the YubiKey 5.8 via the release/1.3.0 of the Yubico Swift SDK. 

Follow the WebAuthnInterceptorSample [README](../WebAuthnInterceptorSample/README.md) to get started. The app defaults to our Yubico demo website that has added support for demonstrating the preview signing extension.

## Run the WebAuthnInterceptorSample

```bash
cd ../WebAuthnInterceptorSample
xed .
```
1. Build and run WebAuthnInterceptorSample on a physical iOS or macOS device
2. The default url (https://demo.yubico.com/webauthn-developers) is loaded into the embedded WebView. 
3. Scroll down to the bottom of the page, expand Extensions
4. Scroll down to PreviewSign and select the checkbox
5. Select CREATE

Expected: The app should intercept the WebAuthn request and then prompt you to insert or tap a YubiKey.

## References

- [previewSign spec (version 4)](https://yubicolabs.github.io/webauthn-sign-extension/)
- [previewSign explainer](https://github.com/w3c/webauthn/blob/main/explainers/raw-signing-extension.md)
- [ARKG specification (IETF draft-bradleylundberg-cfrg-arkg-08)](https://www.ietf.org/archive/id/draft-bradleylundberg-cfrg-arkg-08.html)
