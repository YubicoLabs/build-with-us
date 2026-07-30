using System.Threading;
using System.Threading.Tasks;

namespace PasskeyAutofill.Core
{
    /// <summary>
    /// Result of a PIN prompt: the PIN bytes and whether the user opted to keep
    /// read-only access on this device. <c>Pin</c> is null when cancelled.
    /// </summary>
    public sealed class PinEntryResult
    {
        /// <summary>The entered PIN as bytes, or null if cancelled. Caller zeros it after use.</summary>
        public byte[]? Pin { get; }

        /// <summary>
        /// True if the user checked "Keep read-only access on this PC".
        /// The vault saves the token only when this is set.
        /// </summary>
        public bool Persist { get; }

        public PinEntryResult(byte[]? pin, bool persist)
        {
            Pin = pin;
            Persist = persist;
        }

        /// <summary>A cancelled prompt with no PIN and no persistence.</summary>
        public static PinEntryResult Cancelled() => new(null, false);
    }

    /// <summary>
    /// Collects a FIDO2 PIN from the user. Implemented by the UI layer with a dialog.
    /// Core has no UI dependency and can be reused in any app type.
    /// </summary>
    public interface IPinEntry
    {
        /// <summary>
        /// Provides the PIN and the "keep read-only access" choice.
        /// The caller zeros <see cref="PinEntryResult.Pin"/> after use.
        /// </summary>
        /// <param name="isRetry">
        /// True when a previous PIN in this operation was wrong. An inline UI should
        /// record <paramref name="retriesRemaining"/> and cancel so the user corrects
        /// the PIN on screen instead of looping inside the SDK call.
        /// </param>
        /// <param name="retriesRemaining">
        /// PIN attempts remaining before lockout, or null if the SDK did not report it.
        /// Show this to the user to avoid unexpected lockouts.
        /// </param>
        Task<PinEntryResult> RequestPinAsync(bool isRetry, int? retriesRemaining, CancellationToken cancellationToken);

        /// <summary>
        /// Called when the security key is waiting for a touch (user presence), e.g.
        /// during an assertion. Raised from a background thread; show a non-blocking
        /// "touch your key" hint. There is nothing to return. The wait ends when the
        /// user touches the key or the operation times out.
        /// </summary>
        void NotifyTouchRequired();
    }
}
