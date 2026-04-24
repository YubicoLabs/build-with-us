// ──────────────────────────────────────────────────────────────────────
//  Third-Party Payment
//
//  Registers a credential with the thirdPartyPayment extension,
//  attaches per-credential metadata via
//  credBlob, enforces credProtect policy, and performs a simulated
//  merchant checkout.
//
//    - thirdPartyPayment  Marks a credential for delegated payment use.
//    - credBlob           Stores per-credential metadata (card label).
//    - credProtect        Enforces user verification on every assertion.
//
//  Flow:
//    Step A  Bank registration:  create a payment credential
//    Step B  Merchant checkout:  assert with thirdPartyPayment + credBlob
//
//  Ref: https://docs.yubico.com/yesdk/users-manual/application-fido2/fido2-extensions-thirdpartypayment.html
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
    /// Registers a credential with thirdPartyPayment, credProtect, and
    /// credBlob, then performs a simulated merchant checkout assertion.
    /// </summary>
    public class ThirdPartyPaymentDemo : IQuickstart
    {
        // Simulated relying party: the user's bank.
        private const string BankRpId = "bank.example.com";

        public string Title => "Third-Party Payment";

        public string Description =>
            "thirdPartyPayment extension with credProtect and credBlob for payment flows";

        public void Run(IYubiKeyDevice yubiKey, Func<KeyEntryData, bool> keyCollector)
        {
            using (var fido2Session = new Fido2Session(yubiKey))
            {
                fido2Session.KeyCollector = keyCollector;
                var authenticatorInfo = fido2Session.AuthenticatorInfo;

                // ─── Pre-flight Check ───────────────────────────────────

                // thirdPartyPayment is advertised in getInfo.extensions.
                // Only YubiKeys with firmware 5.8+ support it.
                if (!authenticatorInfo.IsExtensionSupported(Extensions.ThirdPartyPayment))
                {
                    Console.WriteLine("\n---thirdPartyPayment not supported (requires firmware 5.8+).---\n");
                    return;
                }

                // Check companion extensions. These are optional but enhance
                // the payment UX.
                int maxCredBlob = authenticatorInfo.MaximumCredentialBlobLength ?? 0;
                bool hasCredBlob = authenticatorInfo.IsExtensionSupported(Extensions.CredBlob)
                    && maxCredBlob > 0;
                bool hasCredProtect = authenticatorInfo.IsExtensionSupported(Extensions.CredProtect);

                Console.WriteLine("  thirdPartyPayment:  supported");
                Console.WriteLine("  credBlob:           " +
                    (hasCredBlob ? "supported (max " + maxCredBlob + " bytes)" : "not supported"));
                Console.WriteLine("  credProtect:        " +
                    (hasCredProtect ? "supported" : "not supported"));

                // ═══════════════════════════════════════════════════════════════
                //  Step A: Bank Registration
                //
                //  The user registers a payment credential with their bank.
                //  With thirdPartyPayment enabled, this credential can later be
                //  used by a merchant site to confirm a transaction. No redirect
                //  back to the bank is needed.
                // ═══════════════════════════════════════════════════════════════
                Console.WriteLine("\n  STEP A: BANK REGISTRATION");

                var bankRelyingParty = new RelyingParty(BankRpId)
                {
                    Name = BankRpId,
                };
                var userEntity = new UserEntity(Encoding.UTF8.GetBytes("cardholder-01"))
                {
                    Name = "cardholder-01",
                    DisplayName = "Jane Doe",
                };

                var clientDataHash = SHA256.HashData("registration-challenge"u8);
                var makeCredentialParameters = new MakeCredentialParameters(bankRelyingParty, userEntity)
                {
                    ClientDataHash = clientDataHash,
                };

                // 1. thirdPartyPayment: mark this credential for payment use.
                //
                // AddThirdPartyPaymentExtension() sets the thirdPartyPayment
                // extension to true in the MakeCredential request. The YubiKey
                // stores this flag with the credential. During a later assertion,
                // the authenticator will confirm the flag is set.
                makeCredentialParameters.AddThirdPartyPaymentExtension();

                // 2. Discoverable credential (resident key). Required so the
                //    merchant can trigger an assertion without knowing the
                //    credential ID in advance.
                makeCredentialParameters.AddOption("rk", true);

                // 3. credProtect: enforce that every assertion requires user
                //    verification (PIN or biometric). This prevents a stolen
                //    session from silently authorizing transactions.
                if (hasCredProtect)
                {
                    makeCredentialParameters.AddCredProtectExtension(
                        CredProtectPolicy.UserVerificationRequired,
                        authenticatorInfo);
                    Console.WriteLine("  credProtect: UserVerificationRequired");
                }

                // 4. credBlob: attach a card label as per-credential metadata.
                //    Platforms can display this in the payment UI (e.g. "Pay
                //    with Visa 4242") without a network round-trip.
                byte[] credBlobData = Array.Empty<byte>();
                if (hasCredBlob)
                {
                    credBlobData = Encoding.Unicode.GetBytes("Visa 4242");
                    makeCredentialParameters.AddCredBlobExtension(credBlobData, authenticatorInfo);
                    Console.WriteLine("  credBlob: \"" +
                        Encoding.Unicode.GetString(credBlobData) + "\" (" + credBlobData.Length + " bytes)");
                }

                Console.WriteLine("  Touch YubiKey to register...");
                var makeCredentialData = fido2Session.MakeCredential(makeCredentialParameters);

                // Verify the attestation signature to confirm the credential
                // was created by a genuine authenticator.
                bool attValid = makeCredentialData.VerifyAttestation(clientDataHash);
                bool tppEnabled = makeCredentialData.AuthenticatorData.GetThirdPartyPaymentExtension();

                Console.WriteLine("  Attestation valid:      " + attValid);
                Console.WriteLine("  thirdPartyPayment set:  " + tppEnabled);
                Console.WriteLine("  Bank credential registered.\n");

                // ═══════════════════════════════════════════════════════════════
                //  Step B: Merchant Checkout
                //
                //  A merchant site triggers payment confirmation using the bank
                //  credential. The platform (browser/OS) would show something
                //  like "Pay $42.00 with Visa 4242?" instead of a generic
                //  login prompt.
                //
                //  In a real SPC flow, the merchant calls
                //  navigator.credentials.get() with the payment extension.
                //  Here we simulate that at the CTAP level.
                // ═══════════════════════════════════════════════════════════════
                Console.WriteLine("  STEP B: MERCHANT CHECKOUT ($42.00)");

                var checkoutHash = SHA256.HashData("checkout-challenge-42.00-USD"u8);
                var getAssertionParameters = new GetAssertionParameters(bankRelyingParty, checkoutHash);

                // Request thirdPartyPayment confirmation. The authenticator
                // will return true if the credential was created with the
                // extension enabled.
                getAssertionParameters.RequestThirdPartyPayment();

                // Request the credBlob so the platform can display the card label.
                if (hasCredBlob)
                {
                    getAssertionParameters.RequestCredBlobExtension();
                }

                Console.WriteLine("  Touch YubiKey to confirm payment...");
                var assertions = fido2Session.GetAssertions(getAssertionParameters);
                var assertion = assertions[0];

                // ─── Verify Results ─────────────────────────────────────────

                // Check thirdPartyPayment flag. Confirms this credential is
                // authorized for third-party payment flows.
                bool paymentOk = assertion.AuthenticatorData.GetThirdPartyPaymentExtension();
                Console.WriteLine("  Payment authorized:     " + paymentOk);

                // Read back the card label from credBlob.
                if (hasCredBlob)
                {
                    byte[] blob = assertion.AuthenticatorData.GetCredBlobExtension();
                    if (blob.Length > 0)
                    {
                        Console.WriteLine("  Card label:             " + Encoding.Unicode.GetString(blob));
                    }
                }

                Console.WriteLine("\n  Checkout complete. $42.00 confirmed.");
            }
        }
    }
}
