// ──────────────────────────────────────────────────────────────────────
//  Persistent Token and Device Identity
//
//  Implements the primitives that support conditional mediation
//  for hardware security keys:
//
//    - PPUAT            Persistent PIN UV Auth Token
//    - PCMR             Persistent Credential Management Read Only
//    - encIdentifier    Privacy-preserving stable device identifier
//    - encCredStoreState  Cache-invalidation signal for credentials
//
//  Flow:
//    1. Open a FIDO2 session and verify PIN (once).
//    2. Acquire a PPUAT with PCMR permission.
//    3. Decrypt encIdentifier to a stable 16-byte device ID.
//    4. Decrypt encCredStoreState to a 16-byte credential state.
//    5. Enumerate credentials using the PPUAT (no second PIN prompt).
//    6. Reuse the saved PPUAT in a new session without PIN entry.
//
//  Ref: https://docs.yubico.com/yesdk/users-manual/application-fido2/fido2-auth-tokens.html
// ──────────────────────────────────────────────────────────────────────

using System;
using System.Security.Cryptography;
using Quickstarts.Common;
using Yubico.YubiKey;
using Yubico.YubiKey.Fido2;

namespace Quickstarts
{
    /// <summary>
    /// Implements PPUAT acquisition, PCMR-scoped credential enumeration,
    /// encIdentifier decryption, and encCredStoreState decryption.
    /// </summary>
    public class DeviceIdentityDemo : IQuickstart
    {
        public string Title => "Device Identity";

        public string Description =>
            "PPUAT, encrypted device identifier, credential store state, cross-session token reuse";

        public void Run(IYubiKeyDevice yubiKey, Func<KeyEntryData, bool> keyCollector)
        {
            // We save the PPUAT here so we can reuse it in a second session.
            byte[] savedPpuat;

            // ═══════════════════════════════════════════════════════════════
            //  Session 1 - Acquire PPUAT, read identity, list credentials
            // ═══════════════════════════════════════════════════════════════
            Console.WriteLine("  Session 1: Acquire PPUAT");

            using (var fido2Session = new Fido2Session(yubiKey))
            {
                fido2Session.KeyCollector = keyCollector;

                // ─── Step 1: Acquire PPUAT ──────────────────────────────

                // GetPersistentPinUvAuthToken() performs PIN or UV verification
                // and requests PersistentCredentialManagementReadOnly (PCMR).
                // The resulting token can:
                //   - Enumerate relying parties and credentials (read-only)
                //   - Decrypt encIdentifier and encCredStoreState
                //   - NOT delete or modify credentials
                //
                // The PPUAT remains valid until the PIN is changed (including
                // when forced by a minimum-PIN-length policy change) or the
                // FIDO2 application is factory-reset.
                fido2Session.GetPersistentPinUvAuthToken();

                if (fido2Session.AuthTokenPersistent is null)
                {
                    Console.WriteLine("  Could not acquire PPUAT. Is a PIN set?");
                    return;
                }

                Console.WriteLine("  PPUAT acquired (one PIN entry).");

                // Save the raw PPUAT bytes so we can pass them to a new session.
                savedPpuat = fido2Session.AuthTokenPersistent.Value.ToArray();

                // ─── Step 2: Decrypt Device Identifier ──────────────────
                Console.WriteLine("\n  encIdentifier");

                // AuthenticatorIdentifier decrypts the encIdentifier blob
                // using a key derived from the PPUAT (HKDF-SHA-256 +
                // AES-128-CBC). The result is a stable 16-byte device ID,
                // constant across PIN changes.
                //
                // Without a valid PPUAT, the encrypted blob is random noise.
                // This prevents cross-origin device fingerprinting.
                ReadOnlyMemory<byte>? deviceId = fido2Session.AuthenticatorIdentifier;

                if (deviceId.HasValue)
                {
                    Console.WriteLine("  Device ID: " + ToHex(deviceId.Value.Span));
                    Console.WriteLine("  Length:    " + deviceId.Value.Length + " bytes");
                }
                else
                {
                    Console.WriteLine("  encIdentifier not available (requires firmware 5.8+).");
                }

                // ─── Step 3: Decrypt Credential Store State ─────────────
                Console.WriteLine("\n  encCredStoreState");

                // The decrypted value changes whenever credentials are
                // added, removed, or the authenticator is reset. Platforms
                // use this as a cache-invalidation signal:
                //
                //   saved state == current state -> cache is still valid
                //   saved state != current state -> re-enumerate credentials
                ReadOnlyMemory<byte>? credState = fido2Session.AuthenticatorCredStoreState;

                if (credState.HasValue)
                {
                    Console.WriteLine("  Cred state: " + ToHex(credState.Value.Span));
                    Console.WriteLine("  Length:      " + credState.Value.Length + " bytes");
                }
                else
                {
                    Console.WriteLine("  encCredStoreState not available (requires firmware 5.8+).");
                }

                // ─── Step 4: Enumerate Credentials ──────────────────────
                Console.WriteLine("\n  Credential Inventory (using PPUAT)");

                // Because we already have a PPUAT, the SDK uses it
                // automatically. No second PIN prompt is triggered.
                var (existing, remaining) = fido2Session.GetCredentialMetadata();
                Console.WriteLine("  Discoverable credentials: " + existing);
                Console.WriteLine("  Remaining slots:          " + remaining);

                if (existing > 0)
                {
                    var rpList = fido2Session.EnumerateRelyingParties();
                    foreach (var currentRp in rpList)
                    {
                        Console.WriteLine("\n  RP: " + currentRp.Id);
                        var credentialList = fido2Session.EnumerateCredentialsForRelyingParty(currentRp);
                        foreach (var currentCredential in credentialList)
                        {
                            string name = currentCredential.User.DisplayName
                                ?? currentCredential.User.Name
                                ?? "(unnamed)";
                            Console.WriteLine("    - " + name);
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            //  Session 2 - Reuse saved PPUAT (zero PIN prompts)
            // ═══════════════════════════════════════════════════════════════
            Console.WriteLine("\n  Session 2: Reuse saved PPUAT");

            // Pass the saved PPUAT into a brand-new Fido2Session.
            // No KeyCollector is needed. The PPUAT is already valid.
            using (var fido2Session = new Fido2Session(yubiKey, persistentPinUvAuthToken: savedPpuat))
            {
                // Verify the device identity matches Session 1.
                ReadOnlyMemory<byte>? deviceId2 = fido2Session.AuthenticatorIdentifier;
                if (deviceId2.HasValue)
                {
                    Console.WriteLine("  Device ID: " + ToHex(deviceId2.Value.Span));
                    Console.WriteLine("  Same device confirmed (no PIN required).");
                }

                // Re-read credential store state to detect changes.
                ReadOnlyMemory<byte>? credState2 = fido2Session.AuthenticatorCredStoreState;
                if (credState2.HasValue)
                {
                    Console.WriteLine("  Cred state: " + ToHex(credState2.Value.Span));
                }

                // Enumerate credentials. Still no PIN prompt.
                var (existing2, _) = fido2Session.GetCredentialMetadata();
                Console.WriteLine("  Credentials: " + existing2);
            }

            // Zero the PPUAT. Don't leave auth tokens in process memory.
            CryptographicOperations.ZeroMemory(savedPpuat);
        }

        private static string ToHex(ReadOnlySpan<byte> bytes) =>
            BitConverter.ToString(bytes.ToArray())
                .Replace("-", string.Empty, StringComparison.Ordinal);
    }
}
