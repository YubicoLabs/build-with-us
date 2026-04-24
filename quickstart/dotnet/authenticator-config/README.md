# GetInfo Discovery

Reads `AuthenticatorInfo` fields supported in YubiKey firmware 5.8. This example is read-only. No credentials are created and no PIN or touch is required.

See the main [project README](../README.md) for setup instructions and requirements.

## Fields

### PIN policy

- `maxPINLength`: Maximum PIN length in Unicode code points. There is also a separate 63-byte limit on the UTF-8 wire encoding, so multi-byte characters can hit the byte ceiling before the code-point ceiling. Check both when validating client-side so you can reject a PIN before the authenticator does and give the user a clear error message.
- `pinComplexityPolicy`: Boolean. When `true`, the authenticator enforces requirements beyond minimum length (e.g. no repeated digits). Query this so you can warn users up front that extra rules apply, instead of letting them hit a vague rejection after they submit.
- `pinComplexityPolicyURL`: Optional URL to a human-readable description of the PIN policy. Independent of `pinComplexityPolicy` and not guaranteed to be present even when complexity is enabled. When available, link users directly so they know exactly what the authenticator expects.
- `uvCountSinceLastPinEntry`: Number of consecutive UV operations since the authenticator last required PIN entry. Use this to implement "re-enter your PIN after N biometric uses" policies so users don't go months without typing their PIN and then forget it.

### Reset capability

- `transportsForReset`: Array of transports that accept the CTAP reset command. USB-only prevents remote reset over NFC, which stops someone from wiping a key without physical USB access.
- `longTouchForReset`: Boolean. When `true`, a sustained touch (~5 seconds) is required to confirm factory reset instead of a quick tap. This prevents accidental resets. Your UX needs to tell the user to hold the key, not just tap it, or the reset will fail silently.

### Attestation

- `attestationFormats`: Supported attestation statement formats. Currently `packed`. This field is future-proofing for when alternate formats (e.g. post-quantum) are standardized. Not much to act on today, but worth logging so you know when that changes.

## Compatibility

On firmware older than 5.8 the authenticator omits these CBOR keys. The SDK returns sensible defaults instead of throwing:

| Field | SDK default when absent |
|---|---|
| `maxPINLength` | `63` (the FIDO2 protocol maximum) |
| `pinComplexityPolicy` | `null` |
| `pinComplexityPolicyUrl` | `null` |
| `uvCountSinceLastPinEntry` | `null` |
| `transportsForReset` | empty list |
| `longTouchForReset` | `false` |
| `attestationFormats` | empty list |

## Run

```bash
dotnet run
```

## Expected output

```
  Firmware: 5.8.0
  AAGUID: F8A011F38C0A4D15800617111F9EDC7D

  PIN Policy
  Max PIN length:    63 code points
  PIN complexity:    Enabled
  Policy URL:        https://example.com
  UV since last PIN: 0

  Reset Discovery
  Transports for reset: usb
  Long touch for reset: True

  Attestation Formats
  Formats: packed, none
```


## References

- [Authenticator configuration](https://docs.yubico.com/yesdk/users-manual/application-fido2/fido2-authenticator-config.html)
- [PIN complexity policy](https://docs.yubico.com/yesdk/users-manual/sdk-programming-guide/pin-complexity-policy.html)
