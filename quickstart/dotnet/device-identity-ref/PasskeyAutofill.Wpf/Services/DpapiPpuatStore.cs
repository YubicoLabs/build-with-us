using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using PasskeyAutofill.Core;

namespace PasskeyAutofill.App.Services
{
    /// <summary>
    /// Persists PPUATs using Windows DPAPI, scoped to the current Windows user.
    /// The device id hex is used as DPAPI entropy, binding each blob to its key.
    ///
    /// Storage: %LOCALAPPDATA%\PasskeyAutofill\ppuat\&lt;deviceIdHex&gt;.bin
    ///
    /// Lives in the WPF project rather than Core because persistence is a platform
    /// concern. To use PasskeyAutofill.Core in another app type, implement
    /// <see cref="IPpuatStore"/> against your platform's secret store:
    ///   macOS / iOS  — Keychain via Security.framework
    ///   Android      — EncryptedSharedPreferences or Android Keystore
    ///   Linux        — libsecret / Secret Service API
    ///   WinUI / MAUI — Windows.Security.Credentials.PasswordVault
    /// </summary>
    public sealed class DpapiPpuatStore : IPpuatStore
    {
        private readonly string _dir;

        public DpapiPpuatStore()
        {
            _dir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "PasskeyAutofill", "ppuat");
        }

        public IReadOnlyCollection<string> DeviceIds
        {
            get
            {
                if (!Directory.Exists(_dir))
                {
                    return Array.Empty<string>();
                }

                return Directory.EnumerateFiles(_dir, "*.bin")
                    .Select(Path.GetFileNameWithoutExtension)
                    .Where(name => !string.IsNullOrEmpty(name))
                    .Select(name => name!)
                    .ToArray();
            }
        }

        public byte[]? TryLoad(string deviceIdHex)
        {
            string path = PathFor(deviceIdHex);
            if (!File.Exists(path))
            {
                return null;
            }

            try
            {
                byte[] encrypted = File.ReadAllBytes(path);
                return ProtectedData.Unprotect(
                    encrypted, Entropy(deviceIdHex), DataProtectionScope.CurrentUser);
            }
            catch (CryptographicException)
            {
                // Decryption failed (wrong user, corrupt, or tampered). Remove and return null.
                TryDeleteFile(path);
                return null;
            }
            catch (Exception)
            {
                // Transient IO or access error. Keep the file; the token may still be good.
                return null;
            }
        }

        public void Save(string deviceIdHex, byte[] ppuat)
        {
            Directory.CreateDirectory(_dir);
            byte[] encrypted = ProtectedData.Protect(
                ppuat, Entropy(deviceIdHex), DataProtectionScope.CurrentUser);
            File.WriteAllBytes(PathFor(deviceIdHex), encrypted);
        }

        public void Delete(string deviceIdHex) => TryDeleteFile(PathFor(deviceIdHex));

        public void Clear()
        {
            if (!Directory.Exists(_dir))
            {
                return;
            }

            foreach (string file in Directory.EnumerateFiles(_dir, "*.bin"))
            {
                TryDeleteFile(file);
            }
        }

        private string PathFor(string deviceIdHex) => Path.Combine(_dir, deviceIdHex + ".bin");

        // Bind the blob to the device id so it cannot be reused under a different name.
        // The hex id is not secret; DPAPI's user key provides the secrecy.
        private static byte[] Entropy(string deviceIdHex) =>
            System.Text.Encoding.UTF8.GetBytes(deviceIdHex);

        private static void TryDeleteFile(string path)
        {
            try
            {
                if (File.Exists(path))
                {
                    File.Delete(path);
                }
            }
            catch (IOException)
            {
                // Best-effort cleanup.
            }
        }
    }
}
