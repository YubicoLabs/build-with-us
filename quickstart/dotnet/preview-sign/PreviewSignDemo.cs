// ──────────────────────────────────────────────────────────────────────
//  previewSign quickstart
//
//  Signs raw data with a YubiKey 5.8+ using the previewSign extension.
//
//  Unlike a normal FIDO2 assertion (which signs authenticatorData ||
//  SHA-256(clientData)), previewSign signs the given input unaltered.
//  This makes signatures compatible with existing protocols like
//  PDF signing, JWT, S/MIME, and SPIFFE without any adapter code.
//
//  Keys are generated using ARKG (Asynchronous Remote Key Generation).
//  One MakeCredential gives you pkBl + pkKem.
//  From that you can derive unlimited unique P-256 signing keys
//  offline, without touching the YubiKey. Each derived key is
//  cryptographically unlinkable to any other.
//
//  Algorithm:  ARKG_P256_ESP256 (-65539)
//
//  Flow:
//    Step A  MakeCredential    YubiKey generates pkBl + pkKem.
//                              Store these along with the credential ID.
//    Step B  DerivePublicKey   Offline. Produces a derived public key
//                              (for verifiers) and an ARKG key handle,
//                              also called a "ticket" (for signing).
//    Step C  GetAssertion      Send credential ID + ticket + message.
//                              YubiKey recreates the private key from
//                              the ticket, signs, then discards it.
//    Step D  VerifySignature   Offline. Standard ECDSA P-256 verify
//                              using the derived public key.
//
//  Requires the Yubico .NET SDK fork with previewSign support.
// ──────────────────────────────────────────────────────────────────────

using System;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using Quickstarts.Common;
using Yubico.YubiKey;
using Yubico.YubiKey.Fido2;
using Yubico.YubiKey.Fido2.Cose;

namespace Quickstarts
{
    /// <summary>
    /// Demonstrates the full previewSign + ARKG flow: key generation,
    /// offline derivation, signing, and verification.
    /// </summary>
    public class PreviewSignDemo : IQuickstart
    {
        private const string RpId = "example.com";

        public string Title => "Preview Sign (ARKG)";

        public string Description =>
            "previewSign extension with ARKG key derivation for raw data signing";

        public void Run(IYubiKeyDevice yubiKey, Func<KeyEntryData, bool> keyCollector)
        {
            using (var fido2Session = new Fido2Session(yubiKey))
            {
                fido2Session.KeyCollector = keyCollector;
                var authenticatorInfo = fido2Session.AuthenticatorInfo;

                // ─── Pre-flight check ─────────────────────────────────────
                // The SDK calls getInfo on session open. The YubiKey
                // advertises "previewSign" in its extensions list if it
                // supports the extension (firmware 5.8+).

                if (!authenticatorInfo.IsExtensionSupported(Extensions.PreviewSign))
                {
                    Console.WriteLine("\n---previewSign not supported (requires firmware 5.8+).---\n");

                    bool hasOldName = authenticatorInfo.Extensions?.Contains("sign") == true;
                    if (hasOldName)
                    {
                        Console.WriteLine("  NOTE: This key reports 'sign' instead of 'previewSign'.");
                        Console.WriteLine("  You may need a newer beta firmware.\n");
                    }

                    return;
                }

                Console.WriteLine("  previewSign: supported");
                Console.WriteLine("  Algorithm:   ARKG_P256_ESP256 (" + (int)CoseAlgorithmIdentifier.ArkgP256Esp256 + ")");

                // ═══════════════════════════════════════════════════════════
                //  Step A: Generate Key (MakeCredential with previewSign)
                //
                //  The YubiKey generates two internal key pairs:
                //    skBl/pkBl (blinding) and skKem/pkKem (KEM).
                //  The private keys never leave the device.
                //
                //  We get back:
                //    - pkBl:  used in Step B to derive unique public keys.
                //    - pkKem: used in Step B to produce a "ticket" (ARKG
                //             key handle) so the YubiKey can recreate the
                //             matching private key when signing.
                //    - credential ID: identifies this credential. For
                //             non-discoverable credentials, this is the
                //             encrypted private key material itself.
                //
                //  You store pkBl, pkKem, and the credential ID once
                //  and reuse them for every derivation.
                // ═══════════════════════════════════════════════════════════
                Console.WriteLine("\n  STEP A: GENERATE KEY");

                var relyingParty = new RelyingParty(RpId) { Name = RpId };
                var userEntity = new UserEntity(Encoding.UTF8.GetBytes("signer-01"))
                {
                    Name = "signer-01",
                    DisplayName = "Document Signer",
                };

                // clientDataHash is a stand-in. In a real WebAuthn flow the
                // browser hashes the CollectedClientData JSON. At the CTAP
                // layer (where this SDK operates) no validation is performed
                // on the hash contents.
                var clientDataHash = SHA256.HashData("previewsign-registration-challenge"u8);
                var makeCredParams = new MakeCredentialParameters(relyingParty, userEntity)
                {
                    ClientDataHash = clientDataHash,
                };

                // This encodes { 3: [-65539], 4: 1 } in CTAP2 canonical
                // CBOR and registers it as the "previewSign" extension
                // input. The method also checks IsExtensionSupported and
                // throws NotSupportedException if the YubiKey doesn't
                // advertise previewSign.
                makeCredParams.AddPreviewSignGenerateKeyExtension(
                    authenticatorInfo,
                    new[] { CoseAlgorithmIdentifier.ArkgP256Esp256 });

                Console.WriteLine("  Touch YubiKey to generate signing key pair...");
                var makeCredData = fido2Session.MakeCredential(makeCredParams);

                // The signed extensions map confirms the YubiKey processed
                // the extension. The actual key data is in the unsigned
                // extension outputs (CTAP2 response field 6), not here.
                var extensions = makeCredData.AuthenticatorData.Extensions;
                if (extensions == null || !extensions.ContainsKey(Extensions.PreviewSign))
                {
                    Console.WriteLine("  WARNING: Authenticator did not return previewSign data.");
                    return;
                }

                Console.WriteLine("  Extension acknowledged by authenticator.");

                // GetPreviewSignGeneratedKey reads the unsigned extension
                // outputs, finds the nested attestation object (key 7),
                // walks the authData binary to extract the credential ID
                // (key handle) and COSE key, then reassembles pkBl and
                // pkKem as 65-byte uncompressed EC points.
                var generatedKey = makeCredData.GetPreviewSignGeneratedKey();

                if (generatedKey == null)
                {
                    Console.WriteLine("  ERROR: Could not parse signing key from response.");
                    return;
                }

                // credentialId = the outer FIDO2 credential ID, used for AllowCredential.
                // generatedKey.KeyHandle = the inner previewSign key handle, passed
                // automatically by the SDK when signing (via derivedKey.DeviceKeyHandle).
                byte[] credentialId = makeCredData.AuthenticatorData.CredentialId!.Id.ToArray();
                Console.WriteLine("  Key handle:  " + generatedKey.KeyHandle.Length + " bytes");
                Console.WriteLine("  Signing key pair generated.");
                Console.WriteLine("  Contains blinding key (used to derive in Step B) and key handle (used to sign in Step C).\n");

                // ═══════════════════════════════════════════════════════════
                //  Step B: Derive Public Key (offline, no YubiKey)
                //
                //  You provide two things:
                //    - ikm: 32 random bytes you generate. This is the
                //      randomness that makes each derived key unique and
                //      unlinkable. Different ikm = different key.
                //    - ctx: a label string (e.g. "contract-123") that
                //      scopes the key to a specific purpose.
                //
                //  Already stored in generatedKey from Step A: pkBl and pkKem.
                //  The SDK reads them internally so you only provide ikm and ctx:
                //    - pkBl + ikm + ctx  →  derived public key
                //    - pkKem + ikm + ctx →  ARKG key handle (ticket)
                //
                //  Outputs:
                //    1. Derived public key: a unique P-256 public key.
                //       Share this with anyone who needs to verify signatures.
                //    2. ARKG key handle / "ticket": save this and send it
                //       to the YubiKey in Step C so it can reconstruct the
                //       matching private key.
                //
                //  You can discard ikm after this call. It is baked into
                //  the ticket. This step is pure math. No YubiKey, no
                //  network, no touch. Call it as many times as you want.
                // ═══════════════════════════════════════════════════════════
                Console.WriteLine("  STEP B: DERIVE PUBLIC KEY (offline)");

                byte[] ikm = RandomNumberGenerator.GetBytes(32);
                byte[] ctx = Encoding.UTF8.GetBytes("contract-123");

                var derivedKey = generatedKey.DerivePublicKey(ikm, ctx);

                Console.WriteLine("  Context:     \"" + Encoding.UTF8.GetString(ctx) + "\"");
                Console.WriteLine("  Derived public key: " + derivedKey.PublicKey.Length + " bytes (uncompressed EC point)");
                Console.WriteLine("  ARKG args:   " + derivedKey.ArkgKeyHandle.Length + " bytes (additional args for signing)");
                Console.WriteLine("  Share the derived public key with verifiers.\n");

                // ═══════════════════════════════════════════════════════════
                //  Step C: Sign (GetAssertion with signByCredential)
                //
                //  You pass in derivedKey (from Step B) and the message.
                //  The SDK reads the rest from derivedKey:
                //    - DeviceKeyHandle: carried over from Step A (credential ID)
                //    - ArkgKeyHandle:   carried over from Step B (the ticket)
                //  It also hashes the message internally (SHA-256).
                //
                //  The SDK wraps all of that into one call:
                //    AddPreviewSignByCredentialExtension(derivedKey, message)
                //
                //  The YubiKey reads the ticket, recreates the matching
                //  private key, signs the hash, returns a DER-encoded
                //  ECDSA signature, then discards the private key.
                // ═══════════════════════════════════════════════════════════
                Console.WriteLine("  STEP C: SIGN");

                byte[] message = Encoding.UTF8.GetBytes("Sign this document.");
                Console.WriteLine("  Message:     \"" + Encoding.UTF8.GetString(message) + "\"");

                var getAssertionParams = new GetAssertionParameters(relyingParty, clientDataHash);
                getAssertionParams.AllowCredential(
                    new CredentialId { Id = credentialId });

                getAssertionParams.AddPreviewSignByCredentialExtension(derivedKey, message);

                Console.WriteLine("  Signing with key handle from Step A and ARKG args from Step B...");
                Console.WriteLine("  Touch YubiKey to sign...");
                var assertions = fido2Session.GetAssertions(getAssertionParams);
                var assertion = assertions[0];

                // The signature is in the signed extension output (key 6).
                byte[]? signature = assertion.AuthenticatorData.GetPreviewSignSignature();
                if (signature == null)
                {
                    Console.WriteLine("  ERROR: Could not parse signature from response.");
                    return;
                }

                Console.WriteLine("  Signature:   " + signature.Length + " bytes (DER-encoded ECDSA)");
                Console.WriteLine("  Signed successfully.\n");

                // ═══════════════════════════════════════════════════════════
                //  Step D: Verify Signature (offline, no YubiKey)
                //
                //  You pass the original message and the signature.
                //  Carried over from Step B inside derivedKey: PublicKey.
                //  The SDK reads it internally and runs standard ECDSA verify.
                //  You don't pass the public key in explicitly.
                //
                //  Pass the original message, not the hash. VerifySignature
                //  hashes it internally and handles DER-to-P1363 conversion.
                //
                //  No ARKG knowledge, no YubiKey, no secrets needed.
                //  The derived public key is a plain P-256 key that works
                //  with any ECDSA verifier.
                // ═══════════════════════════════════════════════════════════
                Console.WriteLine("  STEP D: VERIFY SIGNATURE (offline)");
                Console.WriteLine("  Verifying with derived public key from Step B...");

                bool valid = derivedKey.VerifySignature(message, signature);

                Console.WriteLine("  Signature valid: " + valid);

                if (valid)
                {
                    Console.WriteLine("\n  Full previewSign flow completed successfully.");
                }
                else
                {
                    Console.WriteLine("\n  Signature verification failed.");
                }

                Console.WriteLine("\n  SUMMARY");
                Console.WriteLine("  ─────────────────────────────────────────────");
                Console.WriteLine("  Step A: Generate signing key pair              one time, touch YubiKey");
                Console.WriteLine("  Step B: Derive public key + ARKG args          offline, unlimited");
                Console.WriteLine("  Step C: Sign with key handle + ARKG args       touch YubiKey per signature");
                Console.WriteLine("  Step D: Verify with derived public key        offline, anyone can verify");
                Console.WriteLine();
                Console.WriteLine("  One YubiKey, unlimited unique unlinkable keys.");
            }
        }
    }
}
