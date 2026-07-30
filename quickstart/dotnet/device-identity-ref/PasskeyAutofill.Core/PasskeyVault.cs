using System;
using System.Collections.Generic;
using System.Security.Cryptography;
using Yubico.YubiKey;
using Yubico.YubiKey.Fido2;
using Yubico.YubiKey.Fido2.Commands;
using Yubico.YubiKey.Fido2.Cose;

namespace PasskeyAutofill.Core
{
    /// <summary>
    /// PPUAT lifecycle for silent passkey autofill on YubiKey firmware 5.8+.
    ///
    /// Four calls cover the full lifecycle:
    ///   1. <see cref="Read"/>    - called on every connection. Reuses a PPUAT (in memory,
    ///                              or loaded from <see cref="IPpuatStore"/>) with no PIN.
    ///                              Returns <see cref="VaultStatus.NeedsPin"/> when no token exists.
    ///   2. <see cref="Unlock"/>  - one PIN entry. Acquires the PPUAT and lists passkeys.
    ///                              Persists the token if the user opts in.
    ///   3. <see cref="SignIn"/>  - runs a real assertion (touch required), verified locally.
    ///   4. <see cref="SignOut"/> - zeros the token in memory and erases storage.
    ///
    /// Firmware 5.8 primitives used:
    ///   PPUAT              GetPersistentPinUvAuthToken(): long-lived read-only auth token
    ///   encIdentifier      AuthenticatorIdentifier: stable, private device id
    ///   encCredStoreState  AuthenticatorCredStoreState: change signal for the credential store
    ///   PCMR               the read-only permission the PPUAT carries. It can list
    ///                      credentials but cannot delete them or authorize assertions,
    ///                      which is why <see cref="SignIn"/> needs a fresh PIN and touch.
    ///
    /// The PPUAT is persisted only through the injected <see cref="IPpuatStore"/> and only
    /// when the user opts in. The WPF app uses Windows DPAPI; other platforms supply their own store.
    /// </summary>
    public sealed class PasskeyVault : IDisposable
    {
        /// <summary>Firmware below this does not expose the device-identity features.</summary>
        public static readonly FirmwareVersion MinimumFirmware = new(5, 8, 0);

        private readonly IPpuatStore _store;

        // What we remember about the key for this session.
        private byte[]? _deviceId;        // decrypted encIdentifier (stable id)
        private byte[]? _credStoreState;  // decrypted encCredStoreState (change signal)
        private byte[]? _persistentToken; // the PPUAT, reused to skip the PIN
        private IReadOnlyList<Passkey> _passkeys = Array.Empty<Passkey>();
        private int _passkeyCapacity;     // used + remaining slots, from getCredsMetadata

        // SDK details needed to run an assertion, kept out of the public Passkey DTO so
        // the UI never touches SDK types. Keyed by the Passkey instance handed to the UI.
        private readonly Dictionary<Passkey, CredentialDetails> _details = new();

        public PasskeyVault(IPpuatStore store)
        {
            _store = store;
        }

        /// <summary>True if a token for any device is persisted (drives "Forget this PC").</summary>
        public bool HasPersistedToken => _store.DeviceIds.Count > 0;

        /// <summary>
        /// Silent read for a connected key. Resolves a PPUAT from memory or the store
        /// and uses it without a PIN. Returns <see cref="VaultStatus.NeedsPin"/> when no
        /// valid token exists for this key.
        /// </summary>
        public VaultResult Read(IYubiKeyDevice key)
        {
            if (key.FirmwareVersion < MinimumFirmware)
            {
                return Unsupported(key);
            }

            // Try the in-memory token first (fast path for reconnects this session),
            // then validated tokens from the store. A token that doesn't work is NOT
            // proof it is stale, it may belong to a different key, so this silent
            // path never deletes persisted tokens. A genuinely stale persisted entry
            // is overwritten by the next opted-in Unlock (same device id) or removed
            // by "Forget this PC".
            if (_persistentToken is not null)
            {
                VaultResult? result = TryReadWithToken(key, _persistentToken);
                if (result is not null)
                {
                    return result;
                }

                // In-memory token doesn't match this key (e.g. a different key was
                // inserted). Drop session state only; keep persisted copies.
                Forget();
            }

            byte[]? stored = FindStoredTokenForKey(key);
            if (stored is not null)
            {
                VaultResult? result = TryReadWithToken(key, stored);
                if (result is not null)
                {
                    return result;
                }

                // Keep the persisted copy, but scrub this in-memory instance unless the
                // read adopted it into the session before failing (same array).
                if (!ReferenceEquals(_persistentToken, stored))
                {
                    CryptographicOperations.ZeroMemory(stored);
                }
            }

            return VaultResult.NeedsPin(TryGetPinRetries(key));
        }

        /// <summary>
        /// Attempts a silent read with one candidate token. Returns a result when the
        /// read concluded (Ready or Unsupported); returns null when the token doesn't
        /// work for this key, so the caller can try another source or fall back to PIN.
        /// Never touches persisted storage. A transient failure (USB glitch) must not
        /// cost the user their opted-in persistence.
        /// </summary>
        private VaultResult? TryReadWithToken(IYubiKeyDevice key, byte[] token)
        {
            try
            {
                using var fido2 = new Fido2Session(key, persistentPinUvAuthToken: token);

                // Gate on the actual feature, not just firmware: encIdentifier is null
                // on anything that doesn't expose device identity.
                if (fido2.AuthenticatorInfo.EncIdentifier is null)
                {
                    return Unsupported(key);
                }

                ReadOnlyMemory<byte>? deviceId = fido2.AuthenticatorIdentifier;
                if (deviceId is null)
                {
                    // Token failed to decrypt this key's identity (different key, PIN
                    // changed, or key reset). Let the caller try another source.
                    return null;
                }

                // Adopt this token/identity for the session.
                AdoptToken(token, deviceId.Value.ToArray());

                ReadOnlyMemory<byte>? credState = fido2.AuthenticatorCredStoreState;

                bool hit = credState is not null && _credStoreState is not null &&
                    credState.Value.Span.SequenceEqual(_credStoreState);

                if (hit)
                {
                    // encCredStoreState unchanged since last read.
                    // Return the cached passkey list without re-enumerating.
                    var identityHit = BuildIdentity(fido2, key, _deviceId!, _credStoreState!);
                    return VaultResult.Ready(_passkeys, wasSilent: true, identityHit,
                        credStoreHit: true, _passkeyCapacity);
                }
                else
                {
                    // Credential store changed or first read. Re-enumerate.
                    IReadOnlyList<Passkey> passkeys = ListPasskeys(fido2);
                    _passkeys = passkeys;
                    if (credState is not null)
                    {
                        _credStoreState = credState.Value.ToArray();
                    }

                    var identity = BuildIdentity(fido2, key, _deviceId!, _credStoreState ?? Array.Empty<byte>());
                    return VaultResult.Ready(passkeys, wasSilent: true, identity,
                        credStoreHit: false, _passkeyCapacity);
                }
            }
            catch (Exception)
            {
                // Transient or token-related failure. Don't destroy the persisted token.
                // The caller falls back to PIN unlock.
                return null;
            }
        }

        /// <summary>
        /// One PIN entry. Acquires a PPUAT, reads identity, and lists passkeys.
        /// Persists the token if the user opts in via the PIN prompt.
        /// </summary>
        public VaultResult Unlock(IYubiKeyDevice key, IPinEntry pinEntry)
        {
            if (key.FirmwareVersion < MinimumFirmware)
            {
                return Unsupported(key);
            }

            try
            {
                var collector = new PinKeyCollector(pinEntry);
                using var fido2 = new Fido2Session(key) { KeyCollector = collector.Collect };

                if (fido2.AuthenticatorInfo.EncIdentifier is null)
                {
                    return Unsupported(key);
                }

                // One PIN verification. The persistent token survives reconnects.
                fido2.GetPersistentPinUvAuthToken();
                if (fido2.AuthTokenPersistent is null)
                {
                    return VaultResult.Error(
                        "Couldn't unlock the key. Make sure a FIDO2 PIN is set, then try again.");
                }

                ReadOnlyMemory<byte>? deviceId = fido2.AuthenticatorIdentifier;
                ReadOnlyMemory<byte>? credState = fido2.AuthenticatorCredStoreState;
                if (deviceId is null || credState is null)
                {
                    return Unsupported(key);
                }

                byte[] token = fido2.AuthTokenPersistent.Value.ToArray();
                byte[] id = deviceId.Value.ToArray();

                IReadOnlyList<Passkey> passkeys = ListPasskeys(fido2);
                AdoptToken(token, id);
                _credStoreState = credState.Value.ToArray();
                _passkeys = passkeys;

                // Only persist if the user opted in to keep read-only access on this PC.
                if (collector.Persist)
                {
                    _store.Save(Hex(id), token);
                }

                var identity = BuildIdentity(fido2, key, id, _credStoreState);
                return VaultResult.Ready(passkeys, wasSilent: false, identity,
                    credStoreHit: false, _passkeyCapacity);
            }
            catch (OperationCanceledException)
            {
                // The PIN was wrong (the inline UI cancels the SDK retry loop so the
                // user can correct it on screen) or entry was abandoned. Report the
                // remaining tries so the locked screen can warn about lockout.
                return VaultResult.NeedsPin(TryGetPinRetries(key));
            }
            catch (Exception)
            {
                // Deliberately no exception detail: SDK messages can contain CTAP
                // status text
                return VaultResult.Error(
                    "Couldn't unlock the security key. Reconnect it and try again.");
            }
        }

        /// <summary>
        /// Runs a real WebAuthn assertion for the selected passkey. The YubiKey signs a
        /// fresh random challenge and the signature is verified locally against the stored
        /// public key. This is not a remote session; the app acts as the local verifier only.
        /// Assertion requires GetAssertion permission, not the read-only PPUAT, so a touch
        /// (and possibly a PIN) is required.
        /// </summary>
        public SignInResult SignIn(IYubiKeyDevice key, Passkey passkey, IPinEntry pinEntry)
        {
            if (!_details.TryGetValue(passkey, out CredentialDetails? details))
            {
                return SignInResult.Failed(
                    "This passkey is no longer available. Reconnect the key and try again.");
            }

            // VerifyAssertion handles EC algorithms only. YubiKeys create ES256 by default,
            // but credentials registered elsewhere may use Ed25519 or RSA. Check up front
            // rather than letting the verify step throw.
            if (!IsLocallyVerifiable(details.PublicKey.Algorithm))
            {
                return SignInResult.Failed(
                    $"This passkey uses {details.PublicKey.Algorithm}, which this sample only verifies for EC algorithms like ES256. " +
                    "The assertion itself would pass on a real server.");
            }

            try
            {
                var collector = new PinKeyCollector(pinEntry);
                using var fido2 = new Fido2Session(key) { KeyCollector = collector.Collect };

                // Fresh random challenge. In production this comes from the RP; here the
                // app generates it and acts as the local verifier.
                byte[] challenge = RandomNumberGenerator.GetBytes(32);
                byte[] clientDataHash = SHA256.HashData(challenge);

                var parameters = new GetAssertionParameters(details.RelyingParty, clientDataHash);
                parameters.AllowCredential(details.CredentialId);

                // The YubiKey blinks and waits for a touch.
                IReadOnlyList<GetAssertionData> assertions = fido2.GetAssertions(parameters);
                if (assertions.Count == 0)
                {
                    return SignInResult.Failed("No assertion was returned by the security key.");
                }

                GetAssertionData assertion = assertions[0];
                bool verified = assertion.VerifyAssertion(details.PublicKey, clientDataHash);

                return verified
                    ? SignInResult.Success(passkey.AccountName)
                    : SignInResult.Failed("Signature did not verify. The assertion was rejected.");
            }
            catch (OperationCanceledException)
            {
                return SignInResult.Failed("Sign-in cancelled.");
            }
            catch (TimeoutException)
            {
                return SignInResult.Failed(
                    "Sign-in timed out waiting for a touch. Try again, and touch the key when it blinks.");
            }
            catch (Exception)
            {
                // Deliberately no exception detail: SDK messages can contain CTAP
                // status text
                return SignInResult.Failed("Couldn't sign in. Reconnect the key and try again.");
            }
        }

        /// <summary>
        /// Zeros the in-memory token and erases all persisted tokens.
        /// The next connection will require a PIN.
        /// </summary>
        public void SignOut()
        {
            Forget();
            _store.Clear();
        }

        /// <summary>
        /// Zeros the cached PPUAT in memory only. Does not erase persisted storage,
        /// so the next launch can still load a token silently. Safe to call repeatedly.
        /// </summary>
        public void Forget()
        {
            if (_persistentToken is not null)
            {
                CryptographicOperations.ZeroMemory(_persistentToken);
            }

            _persistentToken = null;
            _deviceId = null;
            _credStoreState = null;
            _passkeys = Array.Empty<Passkey>();
            _details.Clear();
        }

        public void Dispose() => Forget();


        /// <summary>
        /// Finds a stored token that matches the connected key by decrypting encIdentifier
        /// with each candidate (AuthenticatorInfo.GetIdentifier) and comparing to the stored key.
        /// Non-matching candidates are zeroed before moving on; they are never deleted from
        /// the store, because a mismatch may simply mean the token belongs to another key.
        /// </summary>
        private byte[]? FindStoredTokenForKey(IYubiKeyDevice key)
        {
            foreach (string deviceIdHex in _store.DeviceIds)
            {
                byte[]? token = _store.TryLoad(deviceIdHex);
                if (token is null)
                {
                    continue;
                }

                try
                {
                    using var fido2 = new Fido2Session(key, persistentPinUvAuthToken: token);
                    ReadOnlyMemory<byte>? id = fido2.AuthenticatorInfo.GetIdentifier(token);
                    if (id is not null && string.Equals(
                        Hex(id.Value.ToArray()), deviceIdHex, StringComparison.OrdinalIgnoreCase))
                    {
                        return token;
                    }
                }
                catch (Exception)
                {
                    // Token does not match this key; try the next one.
                }

                // Candidate didn't match. Scrub the secret before trying the next.
                CryptographicOperations.ZeroMemory(token);
            }

            return null;
        }

        private void AdoptToken(byte[] token, byte[] deviceId)
        {
            // Zero any prior token before replacing it.
            if (_persistentToken is not null && !ReferenceEquals(_persistentToken, token))
            {
                CryptographicOperations.ZeroMemory(_persistentToken);
            }

            _persistentToken = token;
            _deviceId = deviceId;
        }

        private IReadOnlyList<Passkey> ListPasskeys(Fido2Session fido2)
        {
            _details.Clear();
            var result = new List<Passkey>();

            var (discoverable, remaining) = fido2.GetCredentialMetadata();
            _passkeyCapacity = discoverable + remaining;
            if (discoverable == 0)
            {
                return result;
            }

            foreach (var rp in fido2.EnumerateRelyingParties())
            {
                foreach (var cred in fido2.EnumerateCredentialsForRelyingParty(rp))
                {
                    string account = cred.User.Name ?? "(unnamed)";
                    string display = cred.User.DisplayName ?? account;

                    var passkey = new Passkey(
                        rp.Id, account, display, cred.ThirdPartyPayment ?? false);
                    result.Add(passkey);

                    _details[passkey] = new CredentialDetails(
                        rp, cred.CredentialId, cred.CredentialPublicKey);
                }
            }

            return result;
        }

        private static DeviceIdentity BuildIdentity(
            Fido2Session fido2, IYubiKeyDevice key, byte[] deviceId, byte[] credStoreState)
        {
            var info = fido2.AuthenticatorInfo;
            return new DeviceIdentity(
                firmwareVersion: key.FirmwareVersion.ToString(),
                supportsDeviceIdentity: true,
                deviceIdHex: Hex(deviceId),
                credStoreStateHex: Hex(credStoreState),
                supportsThirdPartyPayment: info.IsExtensionSupported("thirdPartyPayment"));
        }

        private static VaultResult Unsupported(IYubiKeyDevice key) =>
            VaultResult.Unsupported(
                $"This security key (firmware {key.FirmwareVersion}) doesn't support silent " +
                "autofill. Firmware 5.8 or later with device-identity support is required.");

        private static string Hex(byte[] bytes) => Convert.ToHexString(bytes).ToLowerInvariant();

        /// <summary>
        /// Queries the key's remaining PIN tries (CTAP getPinRetries, the same call the
        /// Yubico Authenticator makes on every refresh). No PIN needed. Null on failure.
        /// </summary>
        private static int? TryGetPinRetries(IYubiKeyDevice key)
        {
            try
            {
                using var fido2 = new Fido2Session(key);
                (int retries, _) = fido2.Connection
                    .SendCommand(new GetPinRetriesCommand())
                    .GetData();
                return retries;
            }
            catch (Exception)
            {
                return null; // informational only, never block the flow on it
            }
        }

        /// <summary>Returns true if the SDK's VerifyAssertion supports the given algorithm (EC only).</summary>
        private static bool IsLocallyVerifiable(CoseAlgorithmIdentifier algorithm) =>
            algorithm is CoseAlgorithmIdentifier.ES256
                      or CoseAlgorithmIdentifier.ES384
                      or CoseAlgorithmIdentifier.ES512;

        /// <summary>SDK types for one credential, used to run an assertion.</summary>
        private sealed class CredentialDetails
        {
            public RelyingParty RelyingParty { get; }
            public CredentialId CredentialId { get; }
            public CoseKey PublicKey { get; }

            public CredentialDetails(RelyingParty relyingParty, CredentialId credentialId, CoseKey publicKey)
            {
                RelyingParty = relyingParty;
                CredentialId = credentialId;
                PublicKey = publicKey;
            }
        }
    }
}
