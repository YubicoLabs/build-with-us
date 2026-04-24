// ──────────────────────────────────────────────────────────────────────
//  hmac-secret-mc (PRF)
//
//  Derives a 32-byte secret during MakeCredential using the
//  hmac-secret-mc extension (firmware 5.8). Completes in a
//  single user interaction instead of requiring a separate
//  GetAssertion call.
//
//  Ref: https://docs.yubico.com/yesdk/users-manual/application-fido2/hmac-secret.html
// ──────────────────────────────────────────────────────────────────────

using System;
using System.Security.Cryptography;
using System.Text;
using Quickstarts.Common;
using Yubico.YubiKey;
using Yubico.YubiKey.Fido2;

namespace Quickstarts
{
    /// <summary>
    /// Derives a 32-byte secret during MakeCredential using the
    /// hmac-secret-mc extension (firmware 5.8).
    /// </summary>
    public class HmacSecretDemo : IQuickstart
    {
        public string Title => "HMAC-Secret PRF";

        public string Description =>
            "Derive a 32-byte secret during MakeCredential using hmac-secret-mc";

        public void Run(IYubiKeyDevice yubiKey, Func<KeyEntryData, bool> keyCollector)
        {
            using (var fido2Session = new Fido2Session(yubiKey))
            {
                fido2Session.KeyCollector = keyCollector;
                var authenticatorInfo = fido2Session.AuthenticatorInfo;

                if (!authenticatorInfo.IsExtensionSupported(Extensions.HmacSecretMc))
                {
                    Console.WriteLine("\n  hmac-secret-mc is not supported on this YubiKey.\n");
                    return;
                }

                Console.WriteLine("  hmac-secret-mc: supported");

                // The client provides one or two 32-byte salts. The YubiKey
                // returns a 32-byte secret for each salt.
                var relyingParty = new RelyingParty("demo.local")
                {
                    Name = "demo.local",
                };
                var userEntity = new UserEntity(Encoding.UTF8.GetBytes("user1"))
                {
                    Name = "user1",
                    DisplayName = "user1",
                };
                var salt = SHA256.HashData("demo-salt"u8);
                var clientDataHash = SHA256.HashData("challenge"u8);

                var makeCredentialParameters = new MakeCredentialParameters(relyingParty, userEntity)
                {
                    ClientDataHash = clientDataHash,
                };

                // AddHmacSecretMcExtension sets the hmac-secret-mc extension
                // and attaches the salt during MakeCredential, so the derived
                // secret comes back in the registration response itself.
                makeCredentialParameters.AddHmacSecretMcExtension(authenticatorInfo, salt);

                Console.WriteLine("\n  REGISTER + DERIVE (one touch)");
                Console.WriteLine("  Touch YubiKey to register...");
                var makeCredentialData = fido2Session.MakeCredential(makeCredentialParameters);

                // Extract and decrypt the secret from the response.
                byte[] hmacSecret = makeCredentialData.AuthenticatorData
                    .GetHmacSecretExtension(fido2Session.AuthProtocol);

                string hmacSecretString = BitConverter.ToString(hmacSecret)
                    .Replace("-", string.Empty, StringComparison.Ordinal);
                Console.WriteLine("  Secret derived during registration. One touch total.");
                Console.WriteLine(
                    "\n  Derived Secret: " + hmacSecretString +
                    " (" + hmacSecret.Length + " bytes)");
            }
        }
    }
}
