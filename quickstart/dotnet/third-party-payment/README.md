# Third-Party Payment

Uses the `thirdPartyPayment` FIDO2 extension along with `credBlob` and `credProtect`. The example simulates a two-step payment flow: a bank registers a payment credential, then a merchant asserts against it to confirm a transaction.

See the main [project README](../README.md) for setup instructions and requirements.

## Why this matters

Historically, a FIDO credential is tied to the RP that created it. If you bank with Example Bank and shop at Example Store, the store has to redirect you back to the bank's site to verify the transaction. That's extra steps, confusing UX, and a phishing opportunity.

With `thirdPartyPayment`, the bank creates a credential that the merchant can assert against directly. The platform knows it's a payment credential and can surface appropriate UX ("Pay $42.00 with Visa 4242?") instead of a generic login prompt. No redirect, no confusion.

## Extensions used

### thirdPartyPayment

Marks a credential as usable in payment flows initiated by a party other than the relying party that created it. Defined in the W3C [Secure Payment Confirmation](https://www.w3.org/TR/secure-payment-confirmation/) specification.

- At registration: `AddThirdPartyPaymentExtension()` sets a boolean flag on the credential.
- At assertion: `RequestThirdPartyPayment()` asks the authenticator to confirm the flag. `GetThirdPartyPaymentExtension()` returns `true` if the credential was created with the extension.

The extension is a signal to the platform. It does not change the underlying cryptography.

> **Note:** The extension must be supported by both the authenticator and the platform. Check `IsExtensionSupported(Extensions.ThirdPartyPayment)` before registration, and verify `GetThirdPartyPaymentExtension()` in the attestation response to confirm it was actually set.

### credProtect

`AddCredProtectExtension(UserVerificationRequired)` ensures every assertion requires PIN or biometric verification. This prevents a compromised session from performing silent assertions against payment credentials.

### credBlob

`AddCredBlobExtension()` stores a small byte array (up to `MaximumCredentialBlobLength` bytes) on the credential. The example stores a card label (`"Visa 4242"`) so the platform can display it in the payment dialog without a network request to the issuing bank.

At assertion, `RequestCredBlobExtension()` and `GetCredBlobExtension()` retrieve the stored data. This is what lets the platform show "Pay with Visa 4242" in the payment dialog without calling home to the bank first.

## Flow

```
Bank registration (once):
  1. Bank calls MakeCredential with thirdPartyPayment + credProtect + credBlob
  2. Authenticator stores: credential, payment flag, UV-required policy, blob
  3. Bank stores the credential public key

Merchant checkout (per transaction):
  1. Merchant calls GetAssertion with RequestThirdPartyPayment()
  2. Platform displays payment confirmation (e.g. "Pay $42.00 with Visa 4242?")
  3. User verifies with PIN + touch
  4. Authenticator returns: signature, thirdPartyPayment=true, credBlob
  5. Merchant forwards signature to bank
  6. Bank verifies signature and payment flag
```

## Compatibility

`thirdPartyPayment` requires firmware 5.8+. The companion extensions (`credBlob`, `credProtect`) have been available since earlier firmware but are most useful in combination with the payment flag. The example checks all three at runtime and degrades gracefully if `credBlob` or `credProtect` are missing.


## Run

```bash
dotnet run
```

## Expected output

```
  Firmware:  5.8.0
  thirdPartyPayment:  supported
  credBlob:           supported (max 32 bytes)
  credProtect:        supported

  STEP A: BANK REGISTRATION
  credProtect: UserVerificationRequired
  credBlob: "Visa 4242" (18 bytes)
  Touch YubiKey to register...
  Attestation valid:      True
  thirdPartyPayment set:  True
  Bank credential registered.

  STEP B: MERCHANT CHECKOUT ($42.00)
  Touch YubiKey to confirm payment...
  Payment authorized:     True
  Card label:             Visa 4242

  Checkout complete. $42.00 confirmed.
```

## References

- [thirdPartyPayment extension](https://docs.yubico.com/yesdk/users-manual/application-fido2/thirdpartypayment.html)
- [credBlob extension](https://docs.yubico.com/yesdk/users-manual/application-fido2/cred-blobs.html)
- [Secure Payment Confirmation (W3C)](https://www.w3.org/TR/secure-payment-confirmation/)
