using System;
using System.Security.Cryptography;
using System.Text;
using Yubico.YubiKey;

namespace Quickstarts.Common
{
    /// <summary>
    /// Shared key collector for quickstart examples. Handles PIN
    /// verification with retry and complexity feedback, and touch prompts.
    /// </summary>
    public class SampleKeyCollector
    {
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

                case KeyEntryRequest.TouchRequest:
                    Console.WriteLine("  >> TOUCH YOUR YUBIKEY <<");
                    return true;

                case KeyEntryRequest.VerifyFido2Pin:
                    if (keyEntryData.IsRetry)
                    {
                        Console.WriteLine("  \u2717 Wrong PIN. " + keyEntryData.RetriesRemaining + " attempts remaining.");
                        if (keyEntryData.RetriesRemaining == 0)
                        {
                            Console.WriteLine("  PIN is blocked.");
                            return false;
                        }
                    }

                    if (keyEntryData.IsViolatingPinComplexity)
                    {
                        Console.WriteLine("  \u2717 PIN does not meet complexity requirements.");
                    }

                    Console.Write("  Enter FIDO2 PIN: ");
                    string pin = Console.ReadLine() ?? "";
                    byte[] pinBytes = Encoding.UTF8.GetBytes(pin);
                    keyEntryData.SubmitValue(pinBytes);
                    CryptographicOperations.ZeroMemory(pinBytes);
                    return true;

                default:
                    return false;
            }
        }
    }
}
