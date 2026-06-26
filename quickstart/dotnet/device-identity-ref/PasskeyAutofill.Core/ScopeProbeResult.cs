namespace PasskeyAutofill.Core
{
    /// <summary>
    /// Result of a read-only scope probe: the app attempts a credential delete with the
    /// PPUAT and reports whether the key rejected it.
    ///
    /// A correctly-behaving 5.8 key must reject the delete because the PPUAT carries
    /// only the PCMR (Persistent Credential Management Read-Only) permission.
    /// </summary>
    public sealed class ScopeProbeResult
    {
        /// <summary>True if the delete was rejected (the expected, safe outcome).</summary>
        public bool Rejected { get; }

        /// <summary>A plain-language explanation of what was attempted and what happened.</summary>
        public string Message { get; }

        private ScopeProbeResult(bool rejected, string message)
        {
            Rejected = rejected;
            Message = message;
        }

        public static ScopeProbeResult RejectedAsExpected() =>
            new(true,
                "Attempted delete, rejected by the key. The PPUAT is read-only (PCMR) and cannot delete credentials.");

        public static ScopeProbeResult UnexpectedlyAllowed() =>
            new(false,
                "Delete was NOT rejected. This is unexpected for a read-only token.");

        public static ScopeProbeResult NotApplicable(string message) =>
            new(false, message);
    }
}
