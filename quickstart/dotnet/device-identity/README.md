# Persistent Token and Device Identity

Implements the primitives that support conditional mediation for hardware security keys: PPUAT, PCMR, `encIdentifier`, and `encCredStoreState`.

Before firmware 5.8, hardware keys were invisible to passkey autofill. A platform had no way to know which passkeys lived on a YubiKey without asking for the PIN every time. These four features change that: the user enters their PIN once, and from that point on the platform can silently recognize the key, check whether its credentials have changed, and populate the autofill dropdown without another PIN prompt.

See the main [project README](../README.md) for setup instructions and requirements.

## Features

### PPUAT (Persistent PIN UV Auth Token)

A long-lived authentication token acquired through PIN verification. Unlike a standard PIN/UV auth token, a PPUAT persists across sessions and application restarts.

The example calls `GetPersistentPinUvAuthToken()` in Session 1, saves the raw bytes, then passes them into a new `Fido2Session` in Session 2 with no PIN prompt.

This is what makes silent credential discovery possible. IDPs and password managers can query the key on every connection without interrupting the user.

A PPUAT is invalidated when:
- The PIN is changed (including when forced by a minimum-PIN-length policy change)
- The FIDO2 application is factory-reset

### PCMR (Persistent Credential Management Read Only)

The permission level assigned to a PPUAT. It grants read-only access to credential management:

- `GetCredentialMetadata()`: credential count and remaining slot count
- `EnumerateRelyingParties()`: list all RPs with stored credentials
- `EnumerateCredentialsForRelyingParty()`: list credentials per RP

PCMR explicitly does not grant delete or modify permissions. A compromised PPUAT cannot remove credentials.

> **Scope:** A PPUAT with PCMR is for discovery and management only. It cannot perform a `GetAssertion` (i.e. log a user in). Actual login still requires a standard assertion with user interaction. The PPUAT just makes finding which credential to use much faster.

### encIdentifier

A stable 16-byte device identifier, encrypted using a key derived from the PPUAT via HKDF-SHA-256 + AES-128-CBC. The raw `getInfo` response returns a different ciphertext on each call (16-byte IV + 16-byte ciphertext), preventing correlation by observers. Only a valid PPUAT holder can decrypt it to the stable value.

The identifier is constant across PIN changes, allowing a platform to map sessions to a physical authenticator.

**Who can track the YubiKey?**

| Observer | Can they identify the key? | Why |
|---|---|---|
| Random website | No | `getInfo` returns a fresh IV + ciphertext every call |
| Unauthorized app | No | Cannot decrypt without the PPUAT |
| Authorized platform | Yes | Holds the PPUAT, can decrypt to the stable device ID |

The rotating ciphertext is a firmware-level guarantee. Within a single SDK session the value is cached, so repeated reads of `AuthenticatorIdentifier` return the same bytes.

### encCredStoreState

A 16-byte value that changes when credentials are added, removed, or the authenticator is reset. Platforms use it for cache invalidation:

```
saved state == current state  ->  cache is valid, skip enumeration
saved state != current state  ->  re-enumerate credentials
```

This is what makes autofill instant. If the state hasn't changed since last time, the platform already knows what passkeys are on the key and skips enumeration entirely.

### Typical platform flow

```
First connection: User enters PIN
  Platform acquires PPUAT (PCMR permission)
  Decrypts encIdentifier  ->  device ID
  Decrypts encCredStoreState  ->  credential state
  Enumerates credentials
  Caches: { device ID, credential state, credential list }
  Stores PPUAT

Subsequent connections: No PIN required
  Platform loads saved PPUAT into Fido2Session
  Decrypts encIdentifier  ->  same device ID  ->  cache hit
  Decrypts encCredStoreState  ->  compare with cached state
  If unchanged: use cached credential list
  If changed: re-enumerate
```

## Compatibility

On firmware older than 5.8, the YubiKey does not support CTAP 2.2 persistent tokens. `GetPersistentPinUvAuthToken()` returns `null`, and `AuthenticatorIdentifier` / `AuthenticatorCredStoreState` will also be `null`. Always check before attempting decryption:

```csharp
fido2Session.GetPersistentPinUvAuthToken();

if (fido2Session.AuthTokenPersistent is null)
{
    // Firmware doesn't support PPUAT. Fall back to standard PIN flow.
    return;
}
```

## Run

```bash
dotnet run
```

## Expected output

```
  Firmware: 5.8.0

  Session 1: Acquire PPUAT
  Enter PIN: ********
  PPUAT acquired (one PIN entry).

  encIdentifier
  Device ID: a1b2c3d4e5f6...
  Length:    16 bytes

  encCredStoreState
  Cred state: f0e1d2c3b4a5...
  Length:      16 bytes

  Credential Inventory (using PPUAT)
  Discoverable credentials: 2
  Remaining slots:          23

  RP: github.com
    - alice@example.com

  RP: google.com
    - alice

  Session 2: Reuse saved PPUAT
  Device ID: a1b2c3d4e5f6...
  Same device confirmed (no PIN required).
  Cred state: f0e1d2c3b4a5...
  Credentials: 2
```

## References

- [FIDO2 auth tokens (PPUAT)](https://docs.yubico.com/yesdk/users-manual/application-fido2/fido2-auth-tokens.html)
- [Credential management](https://docs.yubico.com/yesdk/users-manual/application-fido2/fido2-cred-mgmt.html)
- [Authenticator configuration](https://docs.yubico.com/yesdk/users-manual/application-fido2/fido2-authenticator-config.html)
