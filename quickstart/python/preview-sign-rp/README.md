# previewSign Relying Party (RP) helper

The `previewSign` FIDO2 extension lets a YubiKey sign arbitrary data with a
hardware-backed key. Signatures are standard ECDSA P-256, so they work with any
verifier: PDF signing, JWT, document signing, EUDI wallet credentials, and so on.

`previewSign` has two sides, and this is the key thing to understand:

| Client (talks to the YubiKey) | Relying Party / server (this tool) |
|---|---|
| **A.** Generate a signing key | **B.** Derive a public key offline |
| **C.** Request a signature | **D.** Verify the signature |

The client SDKs (.NET, Android, iOS) handle steps **A** and **C**. They do
**not** ship ARKG derivation or signature verification, because those are
relying-party concerns that run on your server, not on the device. This tool
fills that gap using the official [`python-fido2`](https://github.com/Yubico/python-fido2)
library.

This helper is platform-neutral. It does not care which client produced the
values you give it. A seed public key is a COSE P-256 key whether it came
from .NET, Android, or iOS.

> **Experimental.** previewSign and ARKG are not final. The ARKG spec is an
> individual IETF draft with no CFRG endorsement, and the algorithm IDs are
> placeholders.

## Prerequisites

| Requirement | Details |
|---|---|
| Python | 3.10 or later |
| python-fido2 | A version with ARKG support (see [requirements.txt](requirements.txt)) |
| A client | One of the quickstart clients to produce the values: [.NET](../../dotnet), [Android](../../android), [iOS](../../ios) |

### Setup

A virtual environment keeps these packages off your system Python. On Windows,
this also avoids picking up the Python bundled with YubiKey Manager.

```bash
python -m venv .venv
# Windows:
.venv\Scripts\activate
# macOS / Linux:
source .venv/bin/activate

pip install -r requirements.txt
```

## Running it

The easiest way is interactive mode. Run it with no arguments and follow the
prompts:

```bash
python rp.py
```

It shows a menu, asks for each value, and prints the results to paste back into
your client.

You can also call the two steps directly, which is handy for scripting:

```bash
# Step B: derive a unique public key plus ARKG args from the seed public key
python rp.py derive --public-key <SEED_PUBLIC_KEY_B64URL>

# Step D: verify a signature against the derived public key
python rp.py verify \
    --public-key <DERIVED_PUBLIC_KEY_B64URL> \
    --message "Sign this document." \
    --signature <SIGNATURE_B64URL>
```

All crypto is done by `python-fido2`. This tool just wraps two of its calls.

## The full flow

The client and this RP exchange values by copy/paste. There is no network and no
server, which keeps the client/RP boundary obvious.

```
[ Client (your platform) ]                [ RP (this tool) ]

A. Generate key on YubiKey
   prints: seedPublicKey      ------->    B. python rp.py derive
                                             prints: derivedPublicKey, arkgArgs

C. Sign with arkgArgs         <-------    paste arkgArgs
   prints: signature          ------->    D. python rp.py verify
                                             prints: valid
```

## The format contract

Values cross between the client and this RP as **base64url**, the same encoding
the WebAuthn JSON layer uses. That is deliberate: a value from any client (.NET,
Android, iOS) is interchangeable, because base64url is what the mobile SDKs
already emit. The encoding is only for copy/paste transport. The underlying data
is raw CBOR, and the SDKs work in bytes.

| Value | What it is | Where the client gets it |
|---|---|---|
| **seedPublicKey** | The previewSign `generatedKey.publicKey`, a CTAP2-canonical CBOR COSE key (pkBl + pkKem) | From the registration response (Step A) |
| **signature** | The raw previewSign signature bytes | From the assertion response (Step C) |

If your client prints those two values as base64url, this RP works with it.
Nothing here is platform-specific.

## Getting the values from a client

### .NET

The [.NET previewSign quickstart](../../dotnet/preview-sign) performs Step A
(generate key) and Step C (sign), and it already prints every value as base64url
and prompts you to paste the RP's results back in. Run it, copy the `seedPublicKey`
it prints, and feed it to `derive`. Paste the returned `arkgArgs` and
`derivedPublicKey` back into the .NET client for Step C, then `verify` the
signature it returns. The .NET client prints the exact `rp.py` commands as you
go, so the two tools work together end to end with no code changes.

### Android

The [Android quickstart](../../android) performs Steps A and C. Once it emits
the seed public key and signature as base64url (it already computes them), use the
same `derive` and `verify` commands above. No changes are needed on the RP side.

### iOS

The [iOS quickstart](../../ios) performs Steps A and C through the WebAuthn
client. Once the seed public key and signature are surfaced as copyable base64url,
use the same `derive` and `verify` commands above.

## How it works under the hood

```python
from fido2 import cbor
from fido2.cose import CoseKey

# Step B: derive (the library does all the ARKG-P256 math)
pk = CoseKey.parse(cbor.decode(seed_public_key_bytes))
derived_key, arkg_args = pk.derive_public_key(ikm, ctx)

# Step D: verify (the library does the ECDSA)
derived_key.verify(message, signature)
```

That is the entire RP surface: `derive_public_key` and `verify`. Everything else
in this tool is argument parsing and printing.

## Reference

- [python-fido2 `examples/sign_arkg.py`](https://github.com/Yubico/python-fido2/blob/main/examples/sign_arkg.py): the upstream end-to-end example this tool is based on
- [previewSign extension spec (v4)](https://yubicolabs.github.io/webauthn-sign-extension/4/#sctn-sign-extension)
- [ARKG IETF draft](https://datatracker.ietf.org/doc/draft-bradleylundberg-cfrg-arkg/)