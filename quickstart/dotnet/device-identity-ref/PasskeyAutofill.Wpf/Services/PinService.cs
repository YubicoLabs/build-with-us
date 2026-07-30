using System;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using PasskeyAutofill.Core;

namespace PasskeyAutofill.App.Services
{
    /// <summary>
    /// Implementation of <see cref="IPinEntry"/>. The PIN field lives on the locked screen.
    ///
    /// The view model captures the PIN from the inline field with <see cref="SetPin"/>
    /// before calling the vault. When the SDK's KeyCollector asks for it, the stored PIN
    /// is handed over immediately. On a wrong PIN (a retry request) it records the
    /// remaining tries and cancels so the user re-enters inline instead of looping in a
    /// hidden prompt.
    ///
    /// The PIN is kept (zeroable bytes) for the rest of the session so a later sign-in
    /// only needs a touch. Cleared on disconnect, "Forget this PC", and app exit.
    /// </summary>
    public sealed class PinService : IPinEntry
    {
        private byte[]? _pin;
        private bool _persist;

        /// <summary>
        /// Raised (on a background thread) when the security key is waiting for a touch.
        /// The view model shows a "touch your key" hint on the Busy screen.
        /// </summary>
        public event Action? TouchRequired;

        /// <summary>PIN tries remaining, reported by the key after a wrong attempt.</summary>
        public int? RetriesRemaining { get; private set; }

        /// <summary>
        /// True when the last operation needed a PIN and none was cached. Happens after
        /// a silent unlock (the PPUAT never required typing the PIN) followed by an
        /// action that does need one, like an assertion. The view model routes to the
        /// locked screen to collect it.
        /// </summary>
        public bool PinWasMissing { get; private set; }

        /// <summary>Clears the missing-PIN marker. Call before starting a new operation.</summary>
        public void ResetPinMissing() => PinWasMissing = false;

        /// <summary>Stores the PIN entered in the inline field for the next vault call.</summary>
        public void SetPin(string pin, bool persist)
        {
            Clear();
            _pin = Encoding.UTF8.GetBytes(pin);
            _persist = persist;
            RetriesRemaining = null;
            PinWasMissing = false;
        }

        /// <summary>Zeros and drops the cached PIN. Call on disconnect, forget, and exit.</summary>
        public void Clear()
        {
            if (_pin is not null)
            {
                CryptographicOperations.ZeroMemory(_pin);
            }

            _pin = null;
            _persist = false;
        }

        public void NotifyTouchRequired() => TouchRequired?.Invoke();

        public Task<PinEntryResult> RequestPinAsync(
            bool isRetry, int? retriesRemaining, CancellationToken cancellationToken)
        {
            if (isRetry)
            {
                // The PIN we supplied was wrong. Record the count for the locked
                // screen and cancel. The user corrects it inline and tries again.
                RetriesRemaining = retriesRemaining;
                Clear();
                return Task.FromResult(PinEntryResult.Cancelled());
            }

            if (_pin is null)
            {
                // No PIN cached this session (silent unlock). Cancel the SDK call and
                // flag it so the UI can collect the PIN and let the user try again.
                PinWasMissing = true;
                return Task.FromResult(PinEntryResult.Cancelled());
            }

            // Hand out a copy: the collector zeroes what it receives, and a later
            // sign-in in this session needs the PIN again.
            return Task.FromResult(new PinEntryResult((byte[])_pin.Clone(), _persist));
        }
    }
}
