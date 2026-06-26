namespace PasskeyAutofill.Core
{
    /// <summary>
    /// The outcome of reading a security key. The UI maps these to plain-language
    /// states; every non-success outcome has a clear next step (see passkey-ux
    /// §6.6 error states and AP-01; never dead-end the user).
    /// </summary>
    public enum VaultStatus
    {
        /// <summary>Read succeeded; <see cref="VaultResult.Passkeys"/> is populated (may be empty).</summary>
        Ready,

        /// <summary>
        /// The key needs a one-time PIN unlock before it can be read silently.
        /// The caller should prompt for the PIN and call the unlock path.
        /// </summary>
        NeedsPin,

        /// <summary>The key's firmware is below 5.8, so silent autofill is unavailable.</summary>
        Unsupported,

        /// <summary>Something went wrong; <see cref="VaultResult.Message"/> explains it.</summary>
        Error,
    }
}
