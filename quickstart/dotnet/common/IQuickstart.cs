using System;
using Yubico.YubiKey;

namespace Quickstarts.Common
{
    /// <summary>
    /// Interface for FIDO2 quickstart examples. Each implementation receives
    /// a connected YubiKey and a key collector for PIN/touch handling.
    /// </summary>
    public interface IQuickstart
    {
        /// <summary>Short name shown in the selection menu.</summary>
        string Title { get; }

        /// <summary>One-line description shown below the menu item.</summary>
        string Description { get; }

        /// <summary>
        /// Run the example against the given YubiKey.
        /// </summary>
        /// <param name="yubiKey">A connected YubiKey device.</param>
        /// <param name="keyCollector">
        /// Callback for PIN entry and touch prompts.
        /// Pass to <c>Fido2Session.KeyCollector</c>.
        /// </param>
        void Run(IYubiKeyDevice yubiKey, Func<KeyEntryData, bool> keyCollector);
    }
}
