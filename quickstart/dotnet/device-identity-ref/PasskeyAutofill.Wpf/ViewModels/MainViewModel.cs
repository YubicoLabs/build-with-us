using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Security;
using System.Threading.Tasks;
using PasskeyAutofill.Core;
using PasskeyAutofill.App.Services;
using Yubico.YubiKey;
using Yubico.YubiKey.Fido2;

namespace PasskeyAutofill.App.ViewModels
{
    /// <summary>
    /// State machine for the main screen. Responds to key connect/disconnect events
    /// and user actions. Reads device identity from the vault on each successful unlock.
    ///
    /// Screens (bound via <see cref="Screen"/>):
    ///   Disconnected - no key plugged in
    ///   Busy         - SDK call in progress
    ///   Locked       - key present, PIN required
    ///   Unlocked     - passkeys listed
    ///   SignedIn     - assertion verified locally
    ///   Empty        - key unlocked, no passkeys registered
    ///   Unsupported  - firmware below 5.8
    ///   Error        - unrecoverable error with a retry option
    /// </summary>
    public sealed class MainViewModel : ObservableObject
    {
        private readonly PasskeyVault _vault;
        private readonly PinService _pinService;
        private readonly KeyMonitor _monitor;

        private IYubiKeyDevice? _key;
        private string _screen = "Disconnected";
        private string _message = string.Empty;
        private string _busyText = "Working…";

        // Sign-in that was interrupted to collect the PIN; resumed after unlock.
        private Passkey? _pendingSignIn;

        // PIN entry
        private string _pin = string.Empty;
        private bool _persistChoice;
        private string _pinStatus = string.Empty;
        private bool _pinStatusIsError;

        // Device-identity surface (populated on a successful read).
        private bool _hasIdentity;
        private string _firmwareVersion = string.Empty;
        private string _deviceId = string.Empty;
        private string _credStoreState = string.Empty;
        private string _cacheStatus = string.Empty;
        private bool _isCacheHit;
        private string _thirdPartyPaymentText = string.Empty;
        private string _passkeyCountText = string.Empty;
        private bool _canForgetPc;

        public MainViewModel(PasskeyVault vault, PinService pinService, KeyMonitor monitor)
        {
            _vault = vault;
            _pinService = pinService;
            _monitor = monitor;

            UnlockCommand = new RelayCommand(
                async () => await UnlockAsync(),
                () => _screen == "Locked" && !string.IsNullOrEmpty(_pin));
            RetryCommand = new RelayCommand(async () => await RefreshAsync(), () => _key is not null);
            SignInCommand = new RelayCommand<Passkey>(async p => await SignInAsync(p), _ => _screen == "Unlocked");
            BackCommand = new RelayCommand(async () => await RefreshAsync(), () => _key is not null);
            ForgetPcCommand = new RelayCommand(ForgetPc, () => _vault.HasPersistedToken);

            _monitor.Connected += OnConnected;
            _monitor.Disconnected += OnDisconnected;

            CanForgetPc = _vault.HasPersistedToken;

            // Reflect any key already plugged in at launch.
            var existing = KeyMonitor.FindAllConnected();
            if (existing.Length > 1)
            {
                Message = "Multiple security keys detected. Insert only your YubiKey 5.8.";
                Screen = "Error";
            }
            else if (existing.Length == 1)
            {
                OnConnected(existing[0]);
            }
        }

        public RelayCommand UnlockCommand { get; }
        public RelayCommand RetryCommand { get; }
        public RelayCommand<Passkey> SignInCommand { get; }
        public RelayCommand BackCommand { get; }
        public RelayCommand ForgetPcCommand { get; }

        /// <summary>The passkeys shown in the picker / list.</summary>
        public ObservableCollection<Passkey> Passkeys { get; } = new();

        /// <summary>Current screen name; the view switches layout on this.</summary>
        public string Screen
        {
            get => _screen;
            private set
            {
                if (SetProperty(ref _screen, value))
                {
                    UnlockCommand.RaiseCanExecuteChanged();
                    RetryCommand.RaiseCanExecuteChanged();
                    SignInCommand.RaiseCanExecuteChanged();
                    BackCommand.RaiseCanExecuteChanged();
                }
            }
        }

        /// <summary>A plain-language message for error / unsupported / empty states.</summary>
        public string Message
        {
            get => _message;
            private set => SetProperty(ref _message, value);
        }

        /// <summary>Text on the Busy screen; upgraded to a touch prompt when the key blinks.</summary>
        public string BusyText
        {
            get => _busyText;
            private set => SetProperty(ref _busyText, value);
        }

        /// <summary>
        /// Called (from a background thread) when the YubiKey is waiting for a touch.
        /// WPF marshals the property-change notification to the UI thread.
        /// </summary>
        public void ShowTouchPrompt() =>
            BusyText = "Touch your security key. It's blinking.";

        // --- inline PIN entry (Locked screen) ---------------------------------------

        /// <summary>The PIN typed in the inline field. Cleared after every attempt.</summary>
        public string Pin
        {
            get => _pin;
            set
            {
                if (SetProperty(ref _pin, value))
                {
                    UnlockCommand.RaiseCanExecuteChanged();
                }
            }
        }

        /// <summary>The "Keep read-only access on this PC" choice. Opt-in, off by default.</summary>
        public bool PersistChoice
        {
            get => _persistChoice;
            set => SetProperty(ref _persistChoice, value);
        }

        /// <summary>
        /// Status under the PIN field: "Incorrect PIN. X tries remaining." after a wrong
        /// attempt, or a persistent low-count warning (≤ 3) like the Authenticator shows.
        /// </summary>
        public string PinStatus
        {
            get => _pinStatus;
            private set
            {
                if (SetProperty(ref _pinStatus, value))
                {
                    OnPropertyChanged(nameof(HasPinStatus));
                }
            }
        }

        public bool HasPinStatus => !string.IsNullOrEmpty(_pinStatus);

        /// <summary>True for a failed attempt (red); false for the advisory low-count hint (amber).</summary>
        public bool PinStatusIsError
        {
            get => _pinStatusIsError;
            private set => SetProperty(ref _pinStatusIsError, value);
        }

        // --- device identity card -------------------------------------------------

        /// <summary>True when a device-identity snapshot is available to show.</summary>
        public bool HasIdentity
        {
            get => _hasIdentity;
            private set => SetProperty(ref _hasIdentity, value);
        }

        public string FirmwareVersion
        {
            get => _firmwareVersion;
            private set => SetProperty(ref _firmwareVersion, value);
        }

        /// <summary>The stable, decrypted device id (hex), from encIdentifier.</summary>
        public string DeviceId
        {
            get => _deviceId;
            private set => SetProperty(ref _deviceId, value);
        }

        /// <summary>The decrypted credential-store state (hex), from encCredStoreState.</summary>
        public string CredStoreState
        {
            get => _credStoreState;
            private set => SetProperty(ref _credStoreState, value);
        }

        /// <summary>Chip text for the last silent re-read: explains whether the key was re-queried or cache was used.</summary>
        public string CacheStatus
        {
            get => _cacheStatus;
            private set
            {
                if (SetProperty(ref _cacheStatus, value))
                {
                    OnPropertyChanged(nameof(HasCacheStatus));
                }
            }
        }

        /// <summary>True when there is a credential-read status chip to display.</summary>
        public bool HasCacheStatus => !string.IsNullOrEmpty(_cacheStatus);

        /// <summary>True when credentials were unchanged (green chip); false when re-read (amber chip).</summary>
        public bool IsCacheHit
        {
            get => _isCacheHit;
            private set => SetProperty(ref _isCacheHit, value);
        }

        /// <summary>"N of M passkeys" footnote under the list (used vs. total slots).</summary>
        public string PasskeyCountText
        {
            get => _passkeyCountText;
            private set => SetProperty(ref _passkeyCountText, value);
        }

        public string ThirdPartyPaymentText
        {
            get => _thirdPartyPaymentText;
            private set => SetProperty(ref _thirdPartyPaymentText, value);
        }

        /// <summary>True when a persisted token exists, so "Forget this PC" is offered.</summary>
        public bool CanForgetPc
        {
            get => _canForgetPc;
            private set
            {
                if (SetProperty(ref _canForgetPc, value))
                {
                    ForgetPcCommand.RaiseCanExecuteChanged();
                }
            }
        }

        // --- key lifecycle --------------------------------------------------------

        private async void OnConnected(IYubiKeyDevice key)
        {
            if (KeyMonitor.FindAllConnected().Length > 1)
            {
                _key = null;
                Message = "Multiple security keys detected. Insert only your YubiKey 5.8.";
                Screen = "Error";
                return;
            }
            _key = key;
            await RefreshAsync();
        }

        private void OnDisconnected()
        {
            _key = null;
            _pendingSignIn = null;
            _pinService.Clear();
            Pin = string.Empty;
            PinStatus = string.Empty;
            Passkeys.Clear();
            Message = string.Empty;
            HasIdentity = false;
            Screen = "Disconnected";
        }

        /// <summary>
        /// Reads the connected key. Tries silently first; falls back to Locked screen
        /// if no valid token is available.
        /// </summary>
        private async Task RefreshAsync()
        {
            if (_key is null)
            {
                Screen = "Disconnected";
                return;
            }

            BusyText = "Working…";
            Screen = "Busy";
            var key = _key;

            try
            {
                // Firmware 0.0.x means the key enumerated before it could be read,
                // usually because a previous process held the FIDO interface. Transient.
                // Re-enumerate briefly instead of misreporting the key as unsupported.
                if (key.FirmwareVersion.Major == 0)
                {
                    IYubiKeyDevice? readable = await ReenumerateReadableKeyAsync();

                    if (!ReferenceEquals(_key, key))
                    {
                        return; // removed or swapped while we waited
                    }

                    if (readable is null)
                    {
                        Message = "Couldn't read the security key. Remove and reinsert it.";
                        Screen = "Error";
                        return;
                    }

                    _key = key = readable;
                }

                VaultResult result = await Task.Run(() => _vault.Read(key));

                // The key may have been removed or swapped during the read; the
                // connect/disconnect events own the UI in that case.
                if (!ReferenceEquals(_key, key))
                {
                    return;
                }

                Apply(result);
            }
            catch (Exception ex)
            {
                if (!ReferenceEquals(_key, key))
                {
                    return;
                }

                Message = Describe(ex);
                Screen = "Error";
            }
        }

        private async Task UnlockAsync()
        {
            if (_key is null || string.IsNullOrEmpty(Pin))
            {
                return;
            }

            // Hand the inline PIN to the service the vault's KeyCollector will ask;
            // clear the field immediately so it never lingers in the view.
            _pinService.SetPin(Pin, PersistChoice);
            Pin = string.Empty;

            BusyText = "Working…";
            Screen = "Busy";
            var key = _key;
            try
            {
                VaultResult result = await Task.Run(() => _vault.Unlock(key, _pinService));

                if (!ReferenceEquals(_key, key))
                {
                    return;
                }

                Apply(result);

                // Back on Locked means the PIN was wrong. Say so, with the count.
                if (result.Status == VaultStatus.NeedsPin)
                {
                    PinStatusIsError = true;
                    PinStatus = result.PinRetriesRemaining is int r
                        ? $"Incorrect PIN. {r} tries remaining."
                        : "Incorrect PIN. Try again.";
                }

                // Resume a sign-in that was interrupted to collect the PIN. The list
                // was re-enumerated, so find the same credential in the new instances.
                if (result.Status == VaultStatus.Ready && _pendingSignIn is not null)
                {
                    Passkey? match = Passkeys.FirstOrDefault(p =>
                        p.RelyingPartyId == _pendingSignIn.RelyingPartyId &&
                        p.AccountName == _pendingSignIn.AccountName);
                    _pendingSignIn = null;

                    if (match is not null)
                    {
                        await SignInAsync(match);
                    }
                }
            }
            catch (Exception ex)
            {
                if (!ReferenceEquals(_key, key))
                {
                    return;
                }

                Message = Describe(ex);
                Screen = "Error";
            }
        }

        /// <summary>
        /// Runs a real assertion for the selected passkey and verifies the signature locally.
        /// </summary>
        private async Task SignInAsync(Passkey passkey)
        {
            if (_key is null)
            {
                return;
            }

            BusyText = "Working…";
            Screen = "Busy";
            _pinService.ResetPinMissing();
            var key = _key;
            try
            {
                SignInResult result = await Task.Run(() => _vault.SignIn(key, passkey, _pinService));

                if (!ReferenceEquals(_key, key))
                {
                    return;
                }

                if (result.Verified)
                {
                    Message = result.Message;
                    Screen = "SignedIn";
                }
                else if (_pinService.PinWasMissing)
                {
                    // After a silent unlock no PIN was ever typed this session, and an
                    // assertion needs one (the read-only PPUAT can't authorize it).
                    // Collect the PIN on the Locked screen and resume this sign-in.
                    _pendingSignIn = passkey;
                    PinStatus = string.Empty;
                    Message = "Enter your PIN to finish signing in.";
                    Screen = "Locked";
                }
                else
                {
                    // Failure shows Error with a retry.
                    Message = result.Message;
                    Screen = "Error";
                }
            }
            catch (Exception ex)
            {
                if (!ReferenceEquals(_key, key))
                {
                    return;
                }

                Message = Describe(ex);
                Screen = "Error";
            }
        }

        /// <summary>
        /// Erases the persisted token and zeros the in-memory one.
        /// Returns to the Locked screen; the next connect requires a PIN.
        /// </summary>
        private void ForgetPc()
        {
            _vault.SignOut();
            _pinService.Clear();
            _pendingSignIn = null;
            CanForgetPc = false;
            Passkeys.Clear();
            HasIdentity = false;
            PinStatus = string.Empty;

            if (_key is null)
            {
                Screen = "Disconnected";
                return;
            }

            Message = "Read-only access cleared. Unlock with PIN to continue.";
            Screen = "Locked";
        }

        private void Apply(VaultResult result)
        {
            CanForgetPc = _vault.HasPersistedToken;

            switch (result.Status)
            {
                case VaultStatus.Ready:
                    Passkeys.Clear();
                    foreach (var passkey in result.Passkeys)
                    {
                        Passkeys.Add(passkey);
                    }

                    // "3 of 25 passkeys": used vs. total discoverable slots.
                    PasskeyCountText = result.PasskeyCapacity > 0
                        ? $"{result.Passkeys.Count} of {result.PasskeyCapacity} passkeys"
                        : $"{result.Passkeys.Count} passkeys";

                    PinStatus = string.Empty;
                    ApplyIdentity(result);
                    Screen = result.Passkeys.Count == 0 ? "Empty" : "Unlocked";
                    Message = string.Empty;
                    break;

                case VaultStatus.NeedsPin:
                    Passkeys.Clear();
                    HasIdentity = false;

                    // Show the low-count warning before any wrong attempt when tries are running low.
                    if (result.PinRetriesRemaining is int retries && retries <= 3)
                    {
                        PinStatusIsError = false;
                        PinStatus = $"{retries} PIN tries remaining before lockout.";
                    }
                    else
                    {
                        PinStatus = string.Empty;
                    }

                    Screen = "Locked";
                    Message = result.Message;
                    break;

                case VaultStatus.Unsupported:
                    Passkeys.Clear();
                    HasIdentity = false;
                    Screen = "Unsupported";
                    Message = result.Message;
                    break;

                default:
                    Passkeys.Clear();
                    HasIdentity = false;
                    Screen = "Error";
                    Message = result.Message;
                    break;
            }
        }

        private void ApplyIdentity(VaultResult result)
        {
            DeviceIdentity? id = result.Identity;
            if (id is null)
            {
                HasIdentity = false;
                return;
            }

            FirmwareVersion = id.FirmwareVersion;
            DeviceId = id.DeviceIdHex;
            CredStoreState = id.CredStoreStateHex;
            ThirdPartyPaymentText = id.SupportsThirdPartyPayment ? "Yes" : "No";

            // Only shown on silent re-reads (PPUAT already in memory). First unlock shows nothing.
            CacheStatus = result.WasSilent
                ? (result.CredStoreHit ? "Served from cache (key not re-queried)" : "Re-read from key")
                : string.Empty;
            IsCacheHit = result.CredStoreHit;

            HasIdentity = true;
        }

        /// <summary>
        /// Re-enumerates until the connected key reads with a real firmware version,
        /// giving a lingering previous process a moment to release the FIDO interface.
        /// Null if no readable key showed up in time.
        /// </summary>
        private static async Task<IYubiKeyDevice?> ReenumerateReadableKeyAsync()
        {
            for (int attempt = 0; attempt < 5; attempt++)
            {
                await Task.Delay(600);
                IYubiKeyDevice? candidate = KeyMonitor.FindAllConnected().FirstOrDefault();
                if (candidate is not null && candidate.FirmwareVersion.Major > 0)
                {
                    return candidate;
                }
            }

            return null;
        }

        /// <summary>
        /// Maps exceptions to plain-language messages. The vault handles most failures
        /// internally; this covers anything that escapes. Never exposes CTAP codes or
        /// exception type names.
        /// </summary>
        private static string Describe(Exception ex) => ex switch
        {
            OperationCanceledException => "Cancelled. Try again when you're ready.",
            TimeoutException => "The security key didn't respond in time. Reconnect it and try again.",
            SecurityException => "The PIN is blocked after too many incorrect attempts. Remove and reinsert the key, then try again.",
            Fido2Exception => "The security key rejected the request. Reconnect it and try again.",
            _ => "Something went wrong talking to the security key. Reconnect it and try again.",
        };
    }
}
