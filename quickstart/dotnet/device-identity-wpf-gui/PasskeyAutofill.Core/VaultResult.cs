using System.Collections.Generic;

namespace PasskeyAutofill.Core
{
    /// <summary>
    /// Result of a read from <see cref="PasskeyVault"/>. Carries the status, passkeys,
    /// device-identity snapshot, and a user-facing message for non-ready states.
    /// </summary>
    public sealed class VaultResult
    {
        public VaultStatus Status { get; }

        /// <summary>Whether this read reused a cached/stored PPUAT (no PIN was needed).</summary>
        public bool WasSilent { get; }

        /// <summary>The discovered passkeys. Empty when none are present or not ready.</summary>
        public IReadOnlyList<Passkey> Passkeys { get; }

        /// <summary>
        /// Firmware-5.8 device-identity snapshot, or null when the read did not
        /// complete (NeedsPin or Error).
        /// </summary>
        public DeviceIdentity? Identity { get; }

        /// <summary>
        /// True when the credential-store state matched the cached value and re-enumeration
        /// was skipped (cache HIT). False on a MISS or first read.
        /// </summary>
        public bool CredStoreHit { get; }

        /// <summary>A plain-language message for the user (used for non-ready states).</summary>
        public string Message { get; }

        /// <summary>
        /// Total discoverable-credential capacity of the key (used + remaining slots,
        /// from getCredsMetadata). Drives the "N of M passkeys" footnote. 0 when unknown.
        /// </summary>
        public int PasskeyCapacity { get; }

        /// <summary>
        /// PIN tries remaining before lockout (from getPinRetries), or null when not
        /// queried. Only set on NeedsPin so the locked screen can warn about low counts.
        /// </summary>
        public int? PinRetriesRemaining { get; }

        private VaultResult(
            VaultStatus status,
            bool wasSilent,
            IReadOnlyList<Passkey> passkeys,
            DeviceIdentity? identity,
            bool credStoreHit,
            string message,
            int passkeyCapacity = 0,
            int? pinRetriesRemaining = null)
        {
            Status = status;
            WasSilent = wasSilent;
            Passkeys = passkeys;
            Identity = identity;
            CredStoreHit = credStoreHit;
            Message = message;
            PasskeyCapacity = passkeyCapacity;
            PinRetriesRemaining = pinRetriesRemaining;
        }

        public static VaultResult Ready(
            IReadOnlyList<Passkey> passkeys,
            bool wasSilent,
            DeviceIdentity identity,
            bool credStoreHit,
            int passkeyCapacity) =>
            new(VaultStatus.Ready, wasSilent, passkeys, identity, credStoreHit, string.Empty,
                passkeyCapacity);

        public static VaultResult NeedsPin(int? pinRetriesRemaining = null) =>
            new(VaultStatus.NeedsPin, false, System.Array.Empty<Passkey>(), null, false,
                "Unlock this security key with your PIN to see your passkeys.",
                pinRetriesRemaining: pinRetriesRemaining);

        public static VaultResult Unsupported(string message) =>
            new(VaultStatus.Unsupported, false, System.Array.Empty<Passkey>(), null, false, message);

        public static VaultResult Error(string message) =>
            new(VaultStatus.Error, false, System.Array.Empty<Passkey>(), null, false, message);
    }
}
