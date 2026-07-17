using System;
using System.Collections.Generic;

namespace PasskeyAutofill.Core
{
    /// <summary>
    /// Persists PPUATs across app restarts so silent autofill survives a relaunch.
    /// Keeps platform storage out of <see cref="PasskeyVault"/>.
    ///
    /// Entries are keyed by the key's decrypted device id (hex). Tokens must be
    /// encrypted at rest. The WPF app uses <c>DpapiPpuatStore</c> (Windows DPAPI,
    /// scoped to the current user). Other platforms implement this interface against
    /// their own secret store.
    /// </summary>
    public interface IPpuatStore
    {
        /// <summary>
        /// Device ids (hex) for which a token is stored. The vault iterates these on
        /// connect to find the token that matches the key.
        /// </summary>
        IReadOnlyCollection<string> DeviceIds { get; }

        /// <summary>
        /// Loads and decrypts the stored token for <paramref name="deviceIdHex"/>.
        /// Returns null if none is stored or decryption fails (wrong user, corrupt file).
        /// </summary>
        byte[]? TryLoad(string deviceIdHex);

        /// <summary>Encrypts and saves <paramref name="ppuat"/> for the given device id.</summary>
        void Save(string deviceIdHex, byte[] ppuat);

        /// <summary>Removes the stored token for one device.</summary>
        void Delete(string deviceIdHex);

        /// <summary>Removes all stored tokens.</summary>
        void Clear();
    }
}
