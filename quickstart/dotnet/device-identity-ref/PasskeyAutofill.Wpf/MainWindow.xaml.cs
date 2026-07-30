using System.ComponentModel;
using PasskeyAutofill.Core;
using PasskeyAutofill.App.Services;
using PasskeyAutofill.App.ViewModels;
using Wpf.Ui.Controls;

namespace PasskeyAutofill.App
{
    public partial class MainWindow : FluentWindow
    {
        private readonly PasskeyVault _vault;
        private readonly KeyMonitor _monitor;
        private readonly PinService _pinService;

        public MainWindow()
        {
            InitializeComponent();

            // Compose the pieces by hand so the dependencies are obvious. In a
            // larger app this would be DI. The DPAPI store is the WPF-side persistence
            // for the PPUAT; Core only knows the IPpuatStore seam.
            _vault = new PasskeyVault(new DpapiPpuatStore());
            _monitor = new KeyMonitor();
            _pinService = new PinService();
            var viewModel = new MainViewModel(_vault, _pinService, _monitor);

            // Surface the SDK's "waiting for touch" callback on the Busy screen so
            // the user knows why the app is waiting (the key is blinking).
            _pinService.TouchRequired += viewModel.ShowTouchPrompt;

            DataContext = viewModel;

            Closing += OnClosing;
        }

        private void OnClosing(object? sender, CancelEventArgs e)
        {
            // Stop listening, zero the cached PPUAT and the session PIN.
            _monitor.Dispose();
            _vault.Forget();
            _pinService.Clear();
        }
    }
}
