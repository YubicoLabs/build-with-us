using System;
using System.Linq;
using System.Windows;
using System.Windows.Threading;
using Yubico.YubiKey;

namespace PasskeyAutofill.App.Services
{
    /// <summary>
    /// Raises security-key connect/disconnect events on the WPF UI thread. Wraps
    /// the SDK's <see cref="YubiKeyDeviceListener"/>, which fires on a background
    /// thread, and filters to the FIDO2 transport so the session uses the right
    /// device handle.
    /// </summary>
    public sealed class KeyMonitor : IDisposable
    {
        private readonly Dispatcher _dispatcher;
        private bool _disposed;

        public event Action<IYubiKeyDevice>? Connected;
        public event Action? Disconnected;

        public KeyMonitor()
        {
            _dispatcher = Application.Current.Dispatcher;
            YubiKeyDeviceListener.Instance.Arrived += OnArrived;
            YubiKeyDeviceListener.Instance.Removed += OnRemoved;
        }

        /// <summary>
        /// All FIDO2-capable keys currently plugged in, deduplicated. Right after a
        /// previous process that held the FIDO interface exits (e.g. this app being
        /// relaunched), the same physical key can enumerate twice: once fully read
        /// and once as a stale, unreadable entry (no serial, firmware 0.0.x). Prefer
        /// readable entries with distinct serials; if nothing was readable, report a
        /// single candidate and let the caller retry reading it.
        /// </summary>
        public static IYubiKeyDevice[] FindAllConnected()
        {
            var keys = YubiKeyDevice.FindByTransport(Transport.HidFido).ToArray();
            if (keys.Length <= 1)
            {
                return keys;
            }

            var readable = keys
                .Where(k => k.SerialNumber is not null)
                .GroupBy(k => k.SerialNumber)
                .Select(g => g.First())
                .ToArray();

            return readable.Length > 0 ? readable : new[] { keys[0] };
        }

        private void OnArrived(object? sender, YubiKeyDeviceEventArgs e)
        {
            // A key that couldn't be fully read yet reports no capabilities. Let it
            // through as potentially FIDO2. The view model retries the read and the
            // vault's firmware/EncIdentifier gates make the final call.
            var capabilities = e.Device.AvailableUsbCapabilities;
            if (capabilities == YubiKeyCapabilities.None ||
                capabilities.HasFlag(YubiKeyCapabilities.Fido2))
            {
                _dispatcher.Invoke(() => Connected?.Invoke(e.Device));
            }
        }

        private void OnRemoved(object? sender, YubiKeyDeviceEventArgs e) =>
            _dispatcher.Invoke(() => Disconnected?.Invoke());

        public void Dispose()
        {
            if (_disposed)
            {
                return;
            }

            YubiKeyDeviceListener.Instance.Arrived -= OnArrived;
            YubiKeyDeviceListener.Instance.Removed -= OnRemoved;

            // Tear down the SDK's singleton listener so its background thread and
            // platform HID handles are released now, not at process teardown. Without
            // this, a relaunch can enumerate the key while the dying process still
            // holds its FIDO interface and read it as firmware 0.0.x.
            YubiKeyDeviceListener.StopListening();

            _disposed = true;
        }
    }
}
