namespace PasskeyAutofill.Core
{
    /// <summary>
    /// Result of a local WebAuthn assertion. The key signs a fresh challenge and the
    /// signature is verified against the stored public key. This is not a remote session;
    /// only the relying party can grant account access. The app acts as the local verifier.
    /// </summary>
    public sealed class SignInResult
    {
        /// <summary>True if the assertion signature verified.</summary>
        public bool Verified { get; }

        /// <summary>The account that signed, for display.</summary>
        public string AccountName { get; }

        /// <summary>A plain-language message (success detail, cancellation, or error).</summary>
        public string Message { get; }

        private SignInResult(bool verified, string accountName, string message)
        {
            Verified = verified;
            AccountName = accountName;
            Message = message;
        }

        public static SignInResult Success(string accountName) =>
            new(true, accountName,
                $"Signed in as {accountName}. Signature verified locally against the credential's public key.");

        public static SignInResult Failed(string message) =>
            new(false, string.Empty, message);
    }
}
