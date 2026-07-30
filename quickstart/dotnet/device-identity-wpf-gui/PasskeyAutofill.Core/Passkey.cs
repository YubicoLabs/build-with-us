namespace PasskeyAutofill.Core
{
    /// <summary>
    /// One discoverable credential on the security key, in a UI-ready shape. The
    /// SDK's <c>RelyingParty</c> and <c>CredentialUserInfo</c> are flattened here so
    /// the rest of the app never touches SDK types.
    /// </summary>
    public sealed class Passkey
    {
        /// <summary>The relying party id, e.g. "acme.com".</summary>
        public string RelyingPartyId { get; }

        /// <summary>The account, e.g. "alice@acme.com".</summary>
        public string AccountName { get; }

        /// <summary>The display name, falling back to the account name.</summary>
        public string DisplayName { get; }

        /// <summary>
        /// True if this credential is payment-enabled (the CTAP 2.2 thirdPartyPayment
        /// field, surfaced per credential during enumeration on firmware 5.8). Shown as
        /// a small badge so the new field is visible.
        /// </summary>
        public bool ThirdPartyPayment { get; }

        public Passkey(
            string relyingPartyId,
            string accountName,
            string displayName,
            bool thirdPartyPayment = false)
        {
            RelyingPartyId = relyingPartyId;
            AccountName = accountName;
            DisplayName = displayName;
            ThirdPartyPayment = thirdPartyPayment;
        }
    }
}
