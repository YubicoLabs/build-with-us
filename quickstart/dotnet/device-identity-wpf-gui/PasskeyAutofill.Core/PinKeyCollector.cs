using System.Security.Cryptography;
using System.Threading;
using Yubico.YubiKey;

namespace PasskeyAutofill.Core
{
    /// <summary>
    /// Bridges the SDK's synchronous KeyCollector delegate to the async <see cref="IPinEntry"/>.
    /// The SDK calls the delegate on a background thread, so blocking with GetAwaiter().GetResult()
    /// is safe; the UI marshals to the UI thread internally.
    ///
    /// <see cref="Persist"/> captures the user's "keep read-only access" choice so the
    /// vault can read it after the SDK call returns.
    /// </summary>
    internal sealed class PinKeyCollector
    {
        private readonly IPinEntry _pinEntry;

        public PinKeyCollector(IPinEntry pinEntry)
        {
            _pinEntry = pinEntry;
        }

        /// <summary>Whether the user asked to persist read-only access during the last PIN prompt.</summary>
        public bool Persist { get; private set; }

        public bool Collect(KeyEntryData keyEntryData)
        {
            if (keyEntryData is null)
            {
                return false;
            }

            switch (keyEntryData.Request)
            {
                case KeyEntryRequest.Release:
                    return true;

                // The key is blinking, waiting for user presence. Tell the user;
                // the SDK ignores the return value for touch requests.
                case KeyEntryRequest.TouchRequest:
                    _pinEntry.NotifyTouchRequired();
                    return true;

                // Decline UV so the SDK falls back to PIN for PPUAT acquisition.
                case KeyEntryRequest.VerifyFido2Uv:
                    return false;

                case KeyEntryRequest.VerifyFido2Pin:
                    PinEntryResult result = _pinEntry
                        .RequestPinAsync(
                            keyEntryData.IsRetry,
                            keyEntryData.RetriesRemaining,
                            CancellationToken.None)
                        .GetAwaiter()
                        .GetResult();

                    Persist = result.Persist;

                    if (result.Pin is null)
                    {
                        return false; // cancelled
                    }

                    try
                    {
                        keyEntryData.SubmitValue(result.Pin);
                        return true;
                    }
                    finally
                    {
                        CryptographicOperations.ZeroMemory(result.Pin);
                    }

                default:
                    return false;
            }
        }
    }
}
