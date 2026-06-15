"""
previewSign Relying Party (RP) helper.

previewSign has two sides:

  CLIENT (talks to the YubiKey)        RP / SERVER (this script)
  -----------------------------        --------------------------
  A. Generate a signing key       -->  B. Derive a public key offline
  C. Request a signature          <--  D. Verify the signature

The client SDKs (.NET, Android, iOS) handle steps A and C. They do NOT ship the
ARKG derivation or signature verification - those are relying-party concerns and
live here, in python-fido2.

This script is platform-agnostic. It does not know or care which client produced
the values. A seed public key is a COSE P-256 key whether it came from .NET,
Android, or iOS.

Run with no arguments for interactive mode:
    python rp.py

Or use subcommands for scripting:
    python rp.py derive --public-key <base64url>
    python rp.py verify --public-key <base64url> --message <str> --signature <base64url>

WARNING: previewSign and ARKG are experimental. The ARKG spec is an individual
IETF draft with no CFRG endorsement.
"""

import argparse
import os
import sys


def _import_fido2():
    """Import fido2 lazily so `--help` works before dependencies are installed."""
    try:
        from fido2 import cbor
        from fido2.cose import CoseKey
    except ImportError:
        sys.exit(
            "Error: python-fido2 is not installed.\n"
            "Install it with:  pip install -r requirements.txt\n"
        )
    return cbor, CoseKey


def _b64(data) -> str:
    """Encode bytes as base64url (no padding) - matches the WebAuthn JSON layer
    and the Android/iOS clients, so values are interchangeable across platforms."""
    import base64
    return base64.urlsafe_b64encode(bytes(data)).decode("ascii").rstrip("=")


def _decode(value: str) -> bytes:
    """Decode a base64url value from a client (padding is re-added if stripped)."""
    import base64
    value = value.strip()
    padded = value + "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(padded)


def _prompt_bytes(prompt: str) -> bytes:
    """Prompt for a base64url value, strip whitespace, decode, retry on error."""
    while True:
        print(f"\n  {prompt}")
        value = input("  > ").strip()
        if not value:
            print("  No value entered - please paste the value.")
            continue
        try:
            return _decode(value)
        except Exception:
            print("  That doesn't look like base64url - please try again.")


def _prompt_str(prompt: str, default: str = "") -> str:
    """Prompt the user for a string value."""
    hint = f" [{default}]" if default else ""
    print(f"\n  {prompt}{hint}")
    value = input("  > ").strip()
    return value if value else default


def do_derive(cbor, CoseKey, public_key_b64: str = "", context: str = "", ikm_hex: str = ""):
    """Step B: derive a unique public key from the seed key, offline."""

    if public_key_b64:
        try:
            seed_public_key = _decode(public_key_b64)
        except Exception:
            print("\n  Error: --public-key is not valid base64url.")
            sys.exit(1)
    else:
        seed_public_key = _prompt_bytes(
            "Paste the seedPublicKey from your client's Step A:"
        )

    try:
        pk = CoseKey.parse(cbor.decode(seed_public_key))
    except Exception as exc:
        print(f"\n  Error parsing seed public key: {exc}")
        print("  Make sure you copied the full value from your client.")
        sys.exit(1)

    if ikm_hex:
        try:
            ikm = bytes.fromhex(ikm_hex)
        except ValueError:
            print("\n  Error: --ikm must be a hex string (e.g. 64 hex chars for 32 bytes).")
            sys.exit(1)
    else:
        ikm = os.urandom(32)
    ctx = (context or "quickstart-context").encode("utf-8")

    derived_key, sign_args = pk.derive_public_key(ikm, ctx)

    derived_key_b64 = _b64(cbor.encode(derived_key))
    sign_args_b64 = _b64(cbor.encode(sign_args))

    print()
    print("  ---------------------------------------------------------")
    print("  STEP B: DERIVE PUBLIC KEY (offline, no YubiKey)")
    print(f"  context:          {ctx.decode()!r}")
    print()
    print("  derivedPublicKey  (keep this - use it for Step D verify):")
    print(f"  {derived_key_b64}")
    print()
    print("  arkgArgs          (paste this into your client for Step C sign):")
    print(f"  {sign_args_b64}")
    print("  ---------------------------------------------------------")
    print()

    return derived_key_b64, sign_args_b64


def do_verify(cbor, CoseKey, public_key_b64: str = "", message: str = "", signature_b64: str = ""):
    """Step D: verify a signature against a derived public key, offline."""

    # Ask for signature first - it was just printed by the client in Step C.
    # derivedPublicKey comes second - user scrolls up to Step B output.
    if signature_b64:
        try:
            signature = _decode(signature_b64)
        except Exception:
            print("\n  Error: --signature is not valid base64url.")
            sys.exit(1)
    else:
        signature = _prompt_bytes(
            "Paste the signature from your client's Step C:"
        )

    if not message:
        message = _prompt_str(
            'Enter the message that was signed:',
            default="Sign this document.",
        )

    if public_key_b64:
        try:
            derived_key_bytes = _decode(public_key_b64)
        except Exception:
            print("\n  Error: --public-key is not valid base64url.")
            sys.exit(1)
    else:
        derived_key_bytes = _prompt_bytes(
            "Paste the derivedPublicKey from Step B:"
        )

    try:
        derived_key = CoseKey.parse(cbor.decode(derived_key_bytes))
    except Exception as exc:
        print(f"\n  Error parsing derived public key: {exc}")
        sys.exit(1)

    print()
    print("  ---------------------------------------------------------")
    print("  STEP D: VERIFY SIGNATURE (offline, no YubiKey)")
    print(f"  message:  {message!r}")

    try:
        derived_key.verify(message.encode("utf-8"), signature)
    except Exception as exc:
        print("  valid:    False")
        print(f"  The signature did not verify: {exc}")
        print("  ---------------------------------------------------------")
        sys.exit(1)

    print("  valid:    True")
    print()
    print("  The YubiKey produced a valid signature over your message.")
    print("  This signature is verifiable by anyone with the derived public key -")
    print("  no YubiKey, no ARKG knowledge, no secrets required.")
    print("  ---------------------------------------------------------")
    print()


def cmd_interactive():
    """Walk the user through Steps B and D interactively."""
    cbor, CoseKey = _import_fido2()

    print()
    print("  previewSign RP helper")
    print("  ---------------------------------------------------------")
    print("  This tool handles the relying-party side of previewSign:")
    print("    B. Derive a public key offline (no YubiKey)")
    print("    D. Verify a signature offline  (no YubiKey)")
    print()
    print("  Your client (.NET, Android, iOS) handles Steps A and C.")
    print("  ---------------------------------------------------------")

    while True:
        print()
        print("  What do you want to do?")
        print("    1 - Step B: Derive a public key from a seed public key")
        print("    2 - Step D: Verify a signature")
        print("    0 - Exit")
        print()
        choice = input("  > ").strip()

        if choice == "0":
            break
        elif choice == "1":
            do_derive(cbor, CoseKey)
        elif choice == "2":
            do_verify(cbor, CoseKey)
        else:
            print("  Please enter 0, 1, or 2.")


def cmd_derive(args):
    cbor, CoseKey = _import_fido2()
    do_derive(cbor, CoseKey,
              public_key_b64=args.public_key,
              context=args.context,
              ikm_hex=args.ikm or "")


def cmd_verify(args):
    cbor, CoseKey = _import_fido2()
    do_verify(cbor, CoseKey,
              public_key_b64=args.public_key,
              message=args.message,
              signature_b64=args.signature)


def main():
    # No arguments: interactive mode
    if len(sys.argv) == 1:
        cmd_interactive()
        return

    parser = argparse.ArgumentParser(
        description="previewSign RP helper - ARKG derive + signature verify.",
        epilog="Run with no arguments for interactive mode.",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    derive = sub.add_parser(
        "derive",
        help="Step B: derive a public key + ARKG args from a seed public key.",
    )
    derive.add_argument(
        "--public-key", required=True, metavar="B64",
        help="Seed public key (base64url) from the client's Step A.",
    )
    derive.add_argument(
        "--context", default="quickstart-context", metavar="STR",
        help="Context label for domain separation (default: %(default)s).",
    )
    derive.add_argument(
        "--ikm", metavar="HEX",
        help="Optional input key material (hex). Random if omitted.",
    )
    derive.set_defaults(func=cmd_derive)

    verify = sub.add_parser(
        "verify",
        help="Step D: verify a signature against a derived public key.",
    )
    verify.add_argument(
        "--public-key", required=True, metavar="B64",
        help="Derived public key (base64url) from the derive step.",
    )
    verify.add_argument(
        "--message", required=True, metavar="STR",
        help="The original message that was signed.",
    )
    verify.add_argument(
        "--signature", required=True, metavar="B64",
        help="Signature (base64url) from the client's Step C.",
    )
    verify.set_defaults(func=cmd_verify)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
