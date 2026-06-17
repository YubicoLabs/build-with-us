# Signing Preview

The `previewSign` FIDO2 extension lets a YubiKey sign raw data. Standard FIDO2 signatures wrap your data in `authenticatorData || SHA-256(clientData)`, which means they only work with FIDO2 verifiers. With previewSign, the signed payload is your input unaltered, so the signature works with any protocol that accepts ECDSA P-256 signatures. The private key never leaves the YubiKey. Examples include PDF signing (PAdES), JWT/DPoP tokens, S/MIME email, C2PA content integrity, and SPIFFE workload identities.

The 5.8 firmware implements this using ARKG (Asynchronous Remote Key Generation), specifically the `ARKG_P256_ESP256` algorithm. ARKG lets you generate unlimited unique signing keys offline from a single YubiKey credential. Each derived key is cryptographically unlinkable: a verifier cannot tell that two derived keys came from the same device. You only need to touch the YubiKey once to set up the signing key pair, and again each time you produce a signature.

Deriving new public keys in between is free, and so is verification. Anyone with the derived public key can verify a signature using any standard ECDSA library, no YubiKey or Yubico SDK required.

previewSign is algorithm-agnostic by design, so future firmware versions may offer different algorithms.

Requires YubiKey firmware 5.8+ and the Yubico .NET SDK (`Yubico.YubiKey`) 1.17.0+. See the main [project README](../README.md) for setup instructions.

> **Early Access Only.** The previewSign extension, its algorithm, and the algorithm ID (-65539) are not final and will change before general availability. Do not use this in production.

> **Two sides: client and relying party.** previewSign splits into a client side and a relying-party (server) side. The client talks to the YubiKey: it enables the extension, reads the generated key back, requests a signature, and reads the signature back (Steps A and C below). This is what the `Yubico.YubiKey` package ships, and what this .NET quickstart does. The relying-party side derives public keys offline (Step B) and verifies signatures (Step D). The SDK does not expose those calls as public API because they need no YubiKey and run on your server. The companion [Python RP quickstart](../../python/preview-sign-rp) handles Steps B and D, and this demo pairs with it directly: it prints the values the RP needs and prompts you to paste the RP's results back in.

## When to use this

previewSign is for signing arbitrary data with hardware-backed keys. It is not for authentication.

Real world examples:

- **Digital identity (EUDI).** Present a wallet credential to different services, each presentation signed with a unique key, so the services cannot correlate you by a shared public key. This is key unlinkability only. Hiding or selectively disclosing attribute values (for example, proving "over 18" without revealing a birthdate) is a property of the credential format, not previewSign.
- **Document signing.** Sign contracts, PDFs, or legal documents. The signature is standard ECDSA P-256, compatible with existing verification tools.
- **Software signing.** Publish a derived public key as a release signing key. When you need to sign a release, touch the YubiKey to produce the signature.
- **Workload identity.** Issue hardware-bound signing keys for SPIFFE or OAuth DPoP without exposing private key material to the host.

Deriving public keys is cheap, so you can generate them speculatively. This matters for short-lived credentials like EUDI attribute certificates that expire after a week: you might derive 20 public keys upfront but only end up signing with a few of them. The unused ones cost nothing.

## How ARKG works

When you create a credential with previewSign, the YubiKey generates two key pairs internally. The private halves (`skBl`, `skKem`) never leave the YubiKey. It gives you the public halves:

- **pkBl (blinding public key):** Used to derive new unique public keys offline. Each derivation produces a new ECDSA P-256 public key that looks completely unrelated to pkBl or any other derived key.
- **pkKem (KEM public key):** Used to produce a ticket (`arkgArgs`) during derivation. This is what you send back to the YubiKey when signing so it can recreate the matching private key on the fly.

Both public halves are returned together inside the `PreviewSignGeneratedKey.PublicKey` property as a single COSE-encoded ARKG key. You do not need to decode this blob by hand. The .NET demo prints it as base64url (it calls this the `seedPublicKey`), and the [Python RP](../../python/preview-sign-rp) parses it and runs the derivation for you using `python-fido2`. See Step B in the flow below.

Algorithm: `ARKG_P256_ESP256` (-65539). The published SDK does not expose this as a `CoseAlgorithmIdentifier` member, so the quickstart names it locally as `(CoseAlgorithmIdentifier)(-65539)`.

## Flow

```
Step A: Generate Key (touch YubiKey, one time setup)           [SDK]
  MakeCredential with the previewSign extension.
  YubiKey returns the generated key (carrying pkBl + pkKem) and a credential ID.
  In a real app, save these to your database. You need them to derive new
  keys and sign later. In this demo they stay in memory since the whole
  flow runs in one session.

  SDK calls:
    makeCredParams.AddPreviewSignGenerateKeyExtension(authInfo, new[] { alg });
    var makeCredData = session.MakeCredential(makeCredParams);
    var generatedKey = makeCredData.GetPreviewSignGeneratedKey();
    // generatedKey.PublicKey  -> COSE ARKG key (pkBl + pkKem)
    // generatedKey.KeyHandle  -> inner previewSign key handle
    // makeCredData.AuthenticatorData.CredentialId.Id -> FIDO2 credential ID

Step B: Derive Public Key (offline, no YubiKey, unlimited times)   [Python RP]
  The .NET client prints the seedPublicKey as base64url. Hand it to the Python RP,
  which derives a unique signing key and an ARKG ticket using python-fido2.

  In a second terminal:
    cd quickstart/python/preview-sign-rp
    python rp.py derive --public-key <seedPublicKey from Step A>

  The RP prints two values:
    - derivedPublicKey: a unique P-256 public key. Share it with verifiers.
    - arkgArgs (the ticket): paste it back into the .NET client to sign.
  Run derive as many times as you want for unlimited unlinkable keys. The RP
  generates fresh randomness (ikm) each time, so every derived key is unique.
  This demo does one derivation per run.

Step C: Sign (touch YubiKey, one touch per signature)          [SDK]
  Paste arkgArgs and derivedPublicKey from Step B back into the .NET client.
  previewSign signs the input unaltered, so the client hashes the message first.

  SDK calls:
    byte[] tbs = SHA256.HashData(message);                 // client hashes the message
    byte[] additionalArgs = FromBase64Url(arkgArgs); // the ticket from the RP (base64url)
    getAssertionParams.AllowCredential(new CredentialId { Id = credentialId }); // from Step A output
    getAssertionParams.AddPreviewSignExtension(generatedKey.KeyHandle, tbs, additionalArgs); // KeyHandle also from Step A
    var assertions = session.GetAssertions(getAssertionParams);
    byte[]? signature = assertions[0].AuthenticatorData.GetPreviewSignSignature();

  The YubiKey reads the ticket, recreates the matching private key, signs
  the digest, returns the signature, then discards the private key.

Step D: Verify (offline, no YubiKey)                           [Python RP]
  Hand the signature, message, and derivedPublicKey to the Python RP. The
  derived key is a plain P-256 key, so the RP just runs standard ECDSA verify.

  In your second terminal:
    python rp.py verify \
      --public-key <derivedPublicKey from Step B> \
      --message "Sign this document." \
      --signature <signature from Step C>

  Anyone with the derived public key can verify. No secrets, no YubiKey needed.
```

### What to store

After Step A (once):
- `credentialId` — passed to `AllowCredential` so the YubiKey knows which credential to use
- `generatedKey.KeyHandle` — passed as the `keyHandle` to `AddPreviewSignExtension` when signing (different from `credentialId`; mixing them causes a firmware error)
- `generatedKey.PublicKey` — the COSE ARKG key carrying pkBl + pkKem, used to derive future keys

After Step B (per derived key):
- Derived public key, to share with verifiers
- ARKG ticket (`arkgArgs`), to request signatures from the YubiKey
- Context label, for your own bookkeeping

The random bytes (`ikm`) the RP uses for each derivation do not need to be stored. They are baked into the ticket.

### What the verifier receives

Three things:
- The original document
- The ECDSA signature
- The derived public key

The derived public key is a standard P-256 key. The verifier does not need to know about ARKG, previewSign, or the YubiKey. They just run standard ECDSA verification.

## previewSign API in the published SDK

The `Yubico.YubiKey` package exposes the following previewSign surface. The quickstart uses all of it directly except `PreviewSignOptions`, which it relies on through the default flags.

- `Extensions.PreviewSign`: the `"previewSign"` extension identifier.
- `MakeCredentialParameters.AddPreviewSignGenerateKeyExtension(authenticatorInfo, algorithms, flags)`: encodes the generate-key extension input and checks for extension support. `flags` defaults to `PreviewSignOptions.RequireUserPresence`.
- `MakeCredentialData.GetPreviewSignGeneratedKey()`: returns a `PreviewSignGeneratedKey` (`KeyHandle`, `PublicKey`, `Algorithm`, `AttestationObject`), or `null` if the extension was not used. Note that `Algorithm` is the *signing protocol* negotiated from the `algorithms` input (`-65539` for ESP256-split-ARKG); it is **not** the COSE `alg` of `PublicKey`. The generated ARKG seed in `PublicKey` carries `alg: -65700` (ARKG-P256), and signing keys derived from it have `alg: -9` (ESP256). The Python RP handles this parsing; the distinction matters only if you write your own derivation.
- `GetAssertionParameters.AddPreviewSignExtension(keyHandle, toBeSigned, additionalArgs)`: registers the sign input. Requires a non-empty allow-list (call `AllowCredential` first) or it throws `InvalidOperationException`. The three values are passed to the YubiKey unchanged.
- `AuthenticatorData.GetPreviewSignSignature()`: reads the signature from the signed extension output, or `null` if absent.
- `PreviewSignOptions`: UP/UV policy for the generated signing key. The quickstart uses the default (`RequireUserPresence`); pass it explicitly to `AddPreviewSignGenerateKeyExtension` to change it.

What the package does **not** provide, because these are relying-party concerns that run off-device. The companion [Python RP quickstart](../../python/preview-sign-rp) handles them with `python-fido2`:

- ARKG key derivation (Step B). The published SDK exposes no public derivation API. The RP derives `(derivedPublicKey, arkgArgs)` from the `seedPublicKey`. If you need to do this in .NET instead, bring your own ARKG-P256 implementation.
- previewSign-specific signature verification (Step D). The derived key is a plain P-256 key, so the RP verifies with standard ECDSA. In .NET you could do the same with `System.Security.Cryptography.ECDsa` or the SDK's `Yubico.YubiKey.Cryptography.EcdsaVerify`.
- A `CoseAlgorithmIdentifier` member for `-65539`. Name it locally.

> **Prefer not to install Python?** You can verify (Step D) in .NET directly with `EcdsaVerify` against the derived public key. Deriving keys (Step B), however, requires an ARKG-P256 implementation, which the published .NET SDK does not expose as public API. You would need to port one or use a library like BouncyCastle. For that reason this quickstart uses the Python RP for both steps.

## Compatibility

previewSign requires firmware 5.8+. The example checks for the `"previewSign"` extension and stops if it is missing.

```csharp
if (!authenticatorInfo.IsExtensionSupported(Extensions.PreviewSign))
{
    // Extension not supported on this device.
    return;
}
```

## Run

This demo runs end to end with the [Python RP](../../python/preview-sign-rp). Use two terminals.

First, set up the Python RP once:

```bash
cd quickstart/python/preview-sign-rp
python -m venv .venv
# Windows: .venv\Scripts\activate
# macOS / Linux: source .venv/bin/activate
pip install fido2
```

Then run the flow:

1. **Terminal 1 (.NET client):** From the repo root, run `cd quickstart/dotnet/preview-sign && dotnet run`. Touch the YubiKey for Step A. The client prints a `seedPublicKey` base64url value.
2. **Terminal 2 (Python RP):** In the directory from the setup step above, run `python rp.py derive --public-key <seedPublicKey>`. The RP prints a `derivedPublicKey` and an `arkgArgs` ticket.
3. **Terminal 1:** Paste `arkgArgs` and `derivedPublicKey` when prompted, then touch the YubiKey to sign. The client prints a `signature` base64url value.
4. **Terminal 2:** `python rp.py verify --public-key <derivedPublicKey> --message "Sign this document." --signature <signature>`. The RP prints `valid: True`.

The .NET client also prints the exact `rp.py` commands to copy as you go, so you do not have to assemble them by hand.

## Expected output

The .NET client prints the base64url values to copy and the `rp.py` commands to run. Abbreviated:

```
  previewSign: supported
  Algorithm:   ARKG_P256_ESP256 (-65539)

  STEP A: GENERATE KEY
  Touch YubiKey to generate signing key pair...
  Signing key pair generated.

  ┌─ Copy these values to the Python RP ────────────────────────────────┐
  seedPublicKey (paste into `rp.py derive --public-key`):
  a501020326...
  credentialId (needed for Step C — keep it):
  7b2c19f0...
  └──────────────────────────────────────────────────────────────────────┘

  STEP B: DERIVE PUBLIC KEY (Python RP)
  Run in a second terminal:
    cd quickstart/python/preview-sign-rp
    python rp.py derive --public-key <seedPublicKey above>

  Paste derivedPublicKey from the Python RP here: <you paste>
  Paste arkgArgs from the Python RP here: <you paste>

  STEP C: SIGN
  Message:     "Sign this document."
  Touch YubiKey to sign...
  Signed successfully.

  ┌─ Copy these values to the Python RP ────────────────────────────────┐
  signature (paste into `rp.py verify --signature`):
  3045022100...
  └──────────────────────────────────────────────────────────────────────┘

  STEP D: VERIFY SIGNATURE (Python RP)
  Run in your second terminal:
    python rp.py verify \
      --public-key <derivedPublicKey> \
      --message "Sign this document." \
      --signature <signature above>

  Full previewSign flow complete.
```

In the Python RP terminal, the final `verify` prints `valid: True`, confirming the YubiKey produced a signature that anyone can verify with the derived public key.

## References

- [previewSign spec (version 4)](https://yubicolabs.github.io/webauthn-sign-extension/)
- [previewSign explainer](https://github.com/w3c/webauthn/blob/main/explainers/raw-signing-extension.md)
- [ARKG specification (IETF draft-bradleylundberg-cfrg-arkg-08)](https://www.ietf.org/archive/id/draft-bradleylundberg-cfrg-arkg-08.html)
- [Yubico .NET SDK FIDO2 docs](https://docs.yubico.com/yesdk/users-manual/application-fido2/fido2-overview.html)
