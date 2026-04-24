// ──────────────────────────────────────────────────────────────────────
//  GetInfo Discovery
//
//  Reads AuthenticatorInfo fields supported in firmware 5.8:
//
//    - PIN policy:   maxPINLength, pinComplexityPolicy,
//                    pinComplexityPolicyURL, uvCountSinceLastPinEntry
//    - Reset:        transportsForReset, longTouchForReset
//    - Attestation:  attestationFormats
//
//  Read-only. No credentials are created. No PIN or touch required.
//
//  Ref: https://docs.yubico.com/yesdk/users-manual/application-fido2/fido2-authenticator-config.html
// ──────────────────────────────────────────────────────────────────────

using System;
using System.Text;
using Quickstarts.Common;
using Yubico.YubiKey;
using Yubico.YubiKey.Fido2;

namespace Quickstarts
{
    /// <summary>
    /// Reads AuthenticatorInfo fields supported in firmware 5.8:
    /// PIN policy, reset capability, and attestation formats.
    /// Read-only. No PIN or touch required.
    /// </summary>
    public class AuthenticatorConfigDemo : IQuickstart
    {
        public string Title => "Authenticator Config";

        public string Description =>
            "Read PIN policy, reset, and attestation fields from GetInfo (no PIN/touch)";

        public void Run(IYubiKeyDevice yubiKey, Func<KeyEntryData, bool> keyCollector)
        {
            using (var fido2Session = new Fido2Session(yubiKey))
            {
                var authenticatorInfo = fido2Session.AuthenticatorInfo;

                string aaguid = BitConverter.ToString(authenticatorInfo.Aaguid.ToArray())
                    .Replace("-", string.Empty, StringComparison.Ordinal);
                Console.WriteLine("  AAGUID: " + aaguid);

                // ═══════════════════════════════════════════════════════════════
                //  1. PIN Policy Discovery
                // ═══════════════════════════════════════════════════════════════
                Console.WriteLine("\n  PIN Policy");

                // maxPINLength: the upper bound the authenticator accepts,
                // measured in Unicode code points. The wire protocol also
                // enforces a 63-byte UTF-8 limit, which can be lower for
                // multi-byte characters. Validate both constraints client-side.
                Console.WriteLine("  Max PIN length:    " + authenticatorInfo.MaximumPinLength + " code points");

                // pinComplexityPolicy: when true the authenticator enforces
                // requirements beyond minimum length (e.g. no repeated digits).
                bool complexity = authenticatorInfo.PinComplexityPolicy ?? false;
                Console.WriteLine("  PIN complexity:    " + (complexity ? "Enabled" : "Disabled"));

                // pinComplexityPolicyURL: optional URL to the human-readable
                // policy definition. Independent of pinComplexityPolicy;
                // not guaranteed to be present even when complexity is enabled.
                if (authenticatorInfo.PinComplexityPolicyUrl.HasValue)
                {
                    string url = Encoding.UTF8.GetString(
                        authenticatorInfo.PinComplexityPolicyUrl.Value.Span);
                    Console.WriteLine("  Policy URL:        " + url);
                }

                // uvCountSinceLastPinEntry: number of consecutive UV operations
                // since the authenticator last required PIN entry.
                int uvCount = authenticatorInfo.UvCountSinceLastPinEntry ?? 0;
                Console.WriteLine("  UV since last PIN: " + uvCount);

                // ═══════════════════════════════════════════════════════════════
                //  2. Reset Discovery
                // ═══════════════════════════════════════════════════════════════
                Console.WriteLine("\n  Reset Discovery");

                // transportsForReset: which transports accept the CTAP reset
                // command. USB-only prevents NFC-based reset attacks.
                if (authenticatorInfo.TransportsForReset.Count > 0)
                {
                    Console.WriteLine("  Transports for reset: " + string.Join(", ", authenticatorInfo.TransportsForReset));
                }
                else
                {
                    Console.WriteLine("  Transports for reset: (not reported)");
                }

                // longTouchForReset: when true, a long touch (~5 seconds) is
                // required to confirm a factory reset.
                Console.WriteLine("  Long touch for reset: " + authenticatorInfo.LongTouchForReset);

                // ═══════════════════════════════════════════════════════════════
                //  3. Attestation Formats
                // ═══════════════════════════════════════════════════════════════
                Console.WriteLine("\n  Attestation Formats");

                // attestationFormats: enumerates the attestation statement
                // formats the authenticator supports.
                if (authenticatorInfo.AttestationFormats.Count > 0)
                {
                    Console.WriteLine("  Formats: " + string.Join(", ", authenticatorInfo.AttestationFormats));
                }
                else
                {
                    Console.WriteLine("  Formats: (not reported)");
                }
            }
        }
    }
}
