namespace PasskeyAutofill.Core
{
    /// <summary>
    /// Snapshot of a security key's firmware-5.8 device-identity surface. Produced by
    /// <see cref="PasskeyVault"/> on every read.
    ///
    /// Fields map to these CTAP 2.3 primitives:
    ///   DeviceIdHex               decrypted encIdentifier (stable, private device id)
    ///   CredStoreStateHex         decrypted encCredStoreState (cache invalidation signal)
    ///   SupportsThirdPartyPayment thirdPartyPayment extension (per-credential)
    /// </summary>
    public sealed class DeviceIdentity
    {
        /// <summary>The key's firmware version, e.g. "5.8.0".</summary>
        public string FirmwareVersion { get; }

        /// <summary>
        /// True if the key exposes the device-identity features (firmware 5.8+).
        /// When false, the rest of the identity fields are placeholders.
        /// </summary>
        public bool SupportsDeviceIdentity { get; }

        /// <summary>
        /// Decrypted, stable 16-byte device id (hex). Stable across reconnects and PIN
        /// entries; changes only on a FIDO2 reset. The raw encIdentifier ciphertext
        /// rotates every getInfo call, so only a PPUAT holder can resolve it.
        /// </summary>
        public string DeviceIdHex { get; }

        /// <summary>
        /// Decrypted 16-byte credential-store state (hex). Changes when a credential
        /// is added, removed, or updated. Used to skip re-enumeration on reconnect
        /// when the value is unchanged.
        /// </summary>
        public string CredStoreStateHex { get; }

        /// <summary>True if the key advertises the thirdPartyPayment extension.</summary>
        public bool SupportsThirdPartyPayment { get; }

        public DeviceIdentity(
            string firmwareVersion,
            bool supportsDeviceIdentity,
            string deviceIdHex,
            string credStoreStateHex,
            bool supportsThirdPartyPayment)
        {
            FirmwareVersion = firmwareVersion;
            SupportsDeviceIdentity = supportsDeviceIdentity;
            DeviceIdHex = deviceIdHex;
            CredStoreStateHex = credStoreStateHex;
            SupportsThirdPartyPayment = supportsThirdPartyPayment;
        }
    }
}
