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
//    Step B  Derive key        Offline. Produces a derived public key
//                              (for verifiers) and an ARKG key handle,
//                              also called a "ticket" (for signing).
//    Step C  GetAssertion      Send credential ID + ticket + message.
//                              YubiKey recreates the private key from
//                              the ticket, signs, then discards it.
//    Step D  Verify            Offline. Standard ECDSA P-256 verify
//                              using the derived public key.
//
//  IMPORTANT: what the published Yubico.YubiKey package gives you:
//    The SDK ships the on-device half of previewSign: enabling the
//    extension, reading the generated key back, requesting a signature,
//    and reading the signature back (Steps A and C below). It does NOT
//    ship an ARKG derivation or signature-verification API. Steps B and
//    D are relying-party-side cryptography that runs on your server with
//    no YubiKey involved. This demo hands them off to the companion
//    Python RP (quickstart/python/preview-sign-rp) which uses
//    python-fido2 to derive and verify.
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

        // ARKG_P256_ESP256. The algorithm requested in the previewSign
        // generate-key extension input. The published Yubico.YubiKey package
        // does not expose this as a CoseAlgorithmIdentifier member, so we name
        // it locally and cast the raw value.
        //
        // -65539 is a placeholder, not a final identifier. The ARKG and
        // WebAuthn sign-extension specs are not ratified, so IANA has not
        // assigned a permanent COSE algorithm number (python-fido2 names the
        // same value ESP256_SPLIT_ARKG_PLACEHOLDER). When the spec finalizes
        // the value will change and the SDK will likely add a real enum
        // member, at which point this local cast goes away. This is also why
        // previewSign is early-access only and not for production.
        private const CoseAlgorithmIdentifier ArkgP256Esp256 = (CoseAlgorithmIdentifier)(-65539);

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
                    return;
                }

                Console.WriteLine("  previewSign: supported");
                Console.WriteLine("  Algorithm:   ARKG_P256_ESP256 (" + (int)ArkgP256Esp256 + ")");

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
                //  You store the generated key (which carries pkBl + pkKem)
                //  and the credential ID once and reuse them for every
                //  derivation.
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
                // advertise previewSign. The default flags value is
                // PreviewSignOptions.RequireUserPresence.
                makeCredParams.AddPreviewSignGenerateKeyExtension(
                    authenticatorInfo,
                    new[] { ArkgP256Esp256 });

                Console.WriteLine("  Touch YubiKey to generate signing key pair...");
                var makeCredData = fido2Session.MakeCredential(makeCredParams);

                // The signed extensions map confirms the YubiKey processed
                // the extension. The actual key data is in the unsigned
                // extension outputs (CTAP2 response field 6), which
                // GetPreviewSignGeneratedKey reads below.
                var extensions = makeCredData.AuthenticatorData.Extensions;
                if (extensions == null || !extensions.ContainsKey(Extensions.PreviewSign))
                {
                    Console.WriteLine("  WARNING: Authenticator did not return previewSign data.");
                    return;
                }

                Console.WriteLine("  Extension acknowledged by authenticator.");

                // GetPreviewSignGeneratedKey reads the unsigned extension
                // outputs, finds the nested attestation object, walks the
                // authData to extract the credential ID (key handle) and
                // the COSE public key. For ARKG_P256_ESP256 that COSE key
                // carries pkBl and pkKem.
                var generatedKey = makeCredData.GetPreviewSignGeneratedKey();

                if (generatedKey == null)
                {
                    Console.WriteLine("  ERROR: Could not parse signing key from response.");
                    return;
                }

                // credentialId = the outer FIDO2 credential ID. You pass it to
                //   AllowCredential() in Step C only.
                // generatedKey.KeyHandle = the inner previewSign key handle.
                //   You pass this (not credentialId) as the keyHandle to
                //   AddPreviewSignExtension() in Step C.
                // generatedKey.PublicKey = the COSE-encoded ARKG public key
                //   (pkBl + pkKem) that the RP consumes in Step B (seedPublicKey).
                byte[] credentialId = makeCredData.AuthenticatorData.CredentialId!.Id.ToArray();

                // Values cross the client/RP boundary as base64url text. This is
                // the same encoding the WebAuthn JSON layer uses (and what the
                // Android/iOS clients emit), so the Python RP accepts values from
                // any client unchanged. Encoding is only for copy/paste transport;
                // the SDK itself works with raw bytes.
                string seedPublicKeyB64 = ToBase64Url(generatedKey.PublicKey.ToArray());

                // Show the values so the user can see what the YubiKey produced,
                // then give them the ready-to-run command so they don't have to
                // assemble it themselves.
                Console.WriteLine("  Signing key pair generated.");
                Console.WriteLine();
                Console.WriteLine("  ┌─ Values from your YubiKey ──────────────────────────────────────────┐");
                Console.WriteLine("  seedPublicKey (the ARKG seed key, pkBl + pkKem):");
                Console.WriteLine("  " + seedPublicKeyB64);
                Console.WriteLine();
                Console.WriteLine("  credentialId (kept automatically for Step C):");
                Console.WriteLine("  " + ToBase64Url(credentialId));
                Console.WriteLine("  └──────────────────────────────────────────────────────────────────────┘");
                Console.WriteLine();

                // ═══════════════════════════════════════════════════════════
                //  Step B: Derive Public Key (Python RP)
                //
                //  We print a single-line command (no backslashes) so it can be
                //  copied and pasted as-is into cmd, PowerShell, or bash. The
                //  Python RP derives a unique P-256 signing key and an ARKG
                //  ticket offline, then prints derivedPublicKey + arkgArgs to
                //  paste back here.
                // ═══════════════════════════════════════════════════════════
                Console.WriteLine("  STEP B: DERIVE PUBLIC KEY (Python RP)");
                Console.WriteLine("  In a second terminal (quickstart/python/preview-sign-rp, venv active),");
                Console.WriteLine("  copy and run this whole line:");
                Console.WriteLine();
                Console.WriteLine("    python rp.py derive --public-key " + seedPublicKeyB64);
                Console.WriteLine();
                Console.WriteLine("  Then paste its two outputs below.");
                Console.WriteLine();
                // Prompt order matches the order the Python RP prints them:
                // derivedPublicKey first, then arkgArgs. Copy top-to-bottom.
                Console.Write("  Paste derivedPublicKey from the Python RP here: ");
                string? derivedPublicKeyB64 = Console.ReadLine()?.Trim();
                if (string.IsNullOrEmpty(derivedPublicKeyB64))
                {
                    Console.WriteLine("  No derivedPublicKey provided. Stopping here.");
                    return;
                }

                Console.Write("  Paste arkgArgs from the Python RP here: ");
                string? arkgArgsB64 = Console.ReadLine()?.Trim();
                if (string.IsNullOrEmpty(arkgArgsB64))
                {
                    Console.WriteLine("  No arkgArgs provided. Stopping here.");
                    Console.WriteLine("  Run the Python RP first, then re-run this demo.");
                    return;
                }

                byte[] additionalArgs;
                try
                {
                    additionalArgs = FromBase64Url(arkgArgsB64);
                }
                catch
                {
                    Console.WriteLine("  ERROR: arkgArgs is not valid base64url.");
                    return;
                }

                Console.WriteLine();

                // ═══════════════════════════════════════════════════════════
                //  Step C: Sign (GetAssertion with previewSign)
                //
                //  The YubiKey reads the ARKG ticket from additionalArgs,
                //  recreates the matching private key, signs the message
                //  digest, then discards the private key.
                // ═══════════════════════════════════════════════════════════
                Console.WriteLine("  STEP C: SIGN");

                byte[] message = Encoding.UTF8.GetBytes("Sign this document.");
                Console.WriteLine("  Message:     \"" + Encoding.UTF8.GetString(message) + "\"");

                // previewSign signs the input unaltered, so hash the message
                // before sending. The digest is what the YubiKey will sign.
                byte[] tbs = SHA256.HashData(message);

                var getAssertionParams = new GetAssertionParameters(relyingParty, clientDataHash);
                // AllowCredential uses the FIDO2 credential ID (64 bytes): identifies which credential.
                // AddPreviewSignExtension uses generatedKey.KeyHandle (34 bytes): the inner previewSign key handle.
                // These are different values; mixing them up causes InvalidLength from the firmware.
                getAssertionParams.AllowCredential(new CredentialId { Id = credentialId });
                getAssertionParams.AddPreviewSignExtension(generatedKey.KeyHandle, tbs, additionalArgs);

                Console.WriteLine("  Touch YubiKey to sign...");
                var assertions = fido2Session.GetAssertions(getAssertionParams);
                var assertion = assertions[0];

                byte[]? signature = assertion.AuthenticatorData.GetPreviewSignSignature();
                if (signature == null)
                {
                    Console.WriteLine("  ERROR: Could not parse signature from response.");
                    return;
                }

                string signatureB64 = ToBase64Url(signature);
                Console.WriteLine("  Signed successfully.");
                Console.WriteLine();
                Console.WriteLine("  ┌─ Values from this signing ──────────────────────────────────────────┐");
                Console.WriteLine("  signature (ECDSA P-256 over your message):");
                Console.WriteLine("  " + signatureB64);
                Console.WriteLine("  └──────────────────────────────────────────────────────────────────────┘");
                Console.WriteLine();

                // ═══════════════════════════════════════════════════════════
                //  Step D: Verify Signature (Python RP)
                //
                //  Single-line command (no backslashes) so it pastes cleanly
                //  into cmd, PowerShell, or bash. The Python RP verifies with
                //  standard ECDSA P-256. Anyone with the derived public key
                //  can verify, no YubiKey or secrets needed.
                // ═══════════════════════════════════════════════════════════
                Console.WriteLine("  STEP D: VERIFY SIGNATURE (Python RP)");
                Console.WriteLine("  In your second terminal, copy and run this whole line:");
                Console.WriteLine();
                Console.WriteLine("    python rp.py verify --public-key " + derivedPublicKeyB64 +
                                  " --message \"Sign this document.\" --signature " + signatureB64);
                Console.WriteLine();
                Console.WriteLine("  Full previewSign flow complete.");
                Console.WriteLine();
                Console.WriteLine("  SUMMARY");
                Console.WriteLine("  ─────────────────────────────────────────────────────────────────");
                Console.WriteLine("  Step A: Generate signing key pair   one time, touch YubiKey  [.NET SDK]");
                Console.WriteLine("  Step B: Derive public key + ticket  offline, unlimited        [Python RP]");
                Console.WriteLine("  Step C: Sign with ARKG ticket       touch YubiKey per sig    [.NET SDK]");
                Console.WriteLine("  Step D: Verify signature            offline, anyone           [Python RP]");
                Console.WriteLine();
                Console.WriteLine("  One YubiKey. Unlimited unique unlinkable signing keys.");
            }
        }

        /// <summary>
        /// Encodes bytes as base64url (RFC 4648 section 5): the URL-safe alphabet with
        /// padding stripped. This is the same encoding the WebAuthn JSON layer
        /// uses, so values are interchangeable with the Android/iOS clients and
        /// the Python RP. Encoding is only for copy/paste transport between the
        /// client and RP; the SDK itself works with raw bytes.
        /// </summary>
        private static string ToBase64Url(byte[] data) =>
            Convert.ToBase64String(data)
                .TrimEnd('=')
                .Replace('+', '-')
                .Replace('/', '_');

        /// <summary>
        /// Decodes a base64url string back to bytes, restoring padding.
        /// </summary>
        private static byte[] FromBase64Url(string value)
        {
            string s = value.Replace('-', '+').Replace('_', '/');
            switch (s.Length % 4)
            {
                case 2: s += "=="; break;
                case 3: s += "="; break;
            }

            return Convert.FromBase64String(s);
        }
    }
}
