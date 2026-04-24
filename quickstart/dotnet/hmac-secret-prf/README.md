# hmac-secret-mc (PRF)

Derives a 32-byte secret during `MakeCredential` using the `hmac-secret-mc` extension. On firmware 5.8 this completes in a single user interaction. Previous firmware required a separate `GetAssertion` call with the `hmac-secret` extension (two interactions).

See the main [project README](../README.md) for setup instructions and requirements.

## Why this matters

Before `hmac-secret-mc`, generating an encryption key from a hardware key meant two user touches: one to register, one to derive the secret via assertion. If your app needs both an authentication credential and an encryption key at signup, that's two taps back to back with no obvious reason from the user's perspective.

With `hmac-secret-mc` the secret comes back in the registration response. One touch, done. This is what makes it practical to do things like:

- **Client-side encryption at signup.** Derive a symmetric key during registration and use it to encrypt data in the browser or app before it ever hits your server.
- **Per-purpose keys from one credential.** Use different salts to derive separate keys for authentication, file encryption, and transaction signing, all from the same credential.
- **Federated key derivation.** A payment provider derives its own key from the merchant's credential using its own salt, no shared state needed.

## How it works

1. The client provides a 32-byte salt.
2. `AddHmacSecretMcExtension()` attaches the salt to the `MakeCredential` request.
3. The authenticator creates the credential, computes HMAC-SHA-256 over the salt using a credential-scoped internal key, and returns the 32-byte result in the registration response.
4. `GetHmacSecretExtension()` extracts and decrypts the secret from `AuthenticatorData`.

The credential's internal key never leaves the authenticator. The output is encrypted in transit using the ECDH-derived shared secret between client and authenticator.

### hmac-secret vs. hmac-secret-mc

| | `hmac-secret` | `hmac-secret-mc` |
|---|---|---|
| When secret is derived | During `GetAssertion` | During `MakeCredential` |
| User interactions | Two (register, then assert) | One (register) |
| Firmware required | Any FIDO2-capable YubiKey | 5.8 or later |

### Two salts

Two 32-byte salts can be provided to receive 64 bytes of output (two independent 32-byte values). This supports key rotation: derive with both old and new salt in a single operation. It's also useful for standard encrypt-then-MAC designs where you need one key for AES encryption and a separate key for HMAC integrity, without reusing the same key for both.

## Compatibility

`hmac-secret` has been supported since the first FIDO2-capable YubiKey (CTAP 2.0). `hmac-secret-mc` requires firmware 5.8+. The example checks for support at runtime:

```csharp
if (!authenticatorInfo.IsExtensionSupported(Extensions.HmacSecretMc))
{
    // Fall back to the two-step hmac-secret flow.
}
```

On the browser side, both extensions surface through the WebAuthn PRF extension. The browser handles the negotiation; your RP doesn't need to know which CTAP extension is being used underneath.

## Run

```bash
dotnet run
```

## Expected output

```
  Firmware: 5.8.0
  hmac-secret-mc: supported

  REGISTER + DERIVE (one touch)
  Enter PIN: ********
  Touch YubiKey to register...
  Secret derived during registration. One touch total.

  Derived Secret: 4A7F2B...C831D0 (32 bytes)
```

## References

- [HMAC-Secret extensions](https://docs.yubico.com/yesdk/users-manual/application-fido2/hmac-secret.html)
- [PRF extension explainer (W3C)](https://github.com/w3c/webauthn/wiki/Explainer:-PRF-extension)