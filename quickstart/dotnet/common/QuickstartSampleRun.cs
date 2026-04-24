using System;
using System.Globalization;
using System.Linq;
using Microsoft.Extensions.Logging;
using Yubico.Core.Logging;
using Yubico.YubiKey;

namespace Quickstarts.Common
{
    // Runs the interactive menu and dispatches selected IQuickstart
    // implementations. Follows the pattern of the SDK's Fido2SampleRun.
    public class QuickstartSampleRun
    {
        // Minimum firmware version accepted by the runner.
        private static readonly FirmwareVersion MinimumFirmware = new FirmwareVersion(5, 8, 0);

        private readonly IQuickstart[] _examples;
        private readonly SampleKeyCollector _keyCollector;
        private IYubiKeyDevice? _yubiKeyChosen;
        private bool _chosenByUser;

        public QuickstartSampleRun(params IQuickstart[] examples)
        {
            _examples = examples;
            _keyCollector = new SampleKeyCollector();
        }

        // Run the sample.
        // Run the main menu, then based on the item chosen, run the appropriate
        // operation. After running the operation, return to the main menu.
        // Keep doing this until the user calls for Exit or enters too many
        // invalid responses in a row.
        public void RunSample()
        {
            Log.ConfigureLoggerFactory(builder =>
                builder.ClearProviders()
                    .AddConsole()
                    .AddFilter(level => level >= LogLevel.Critical));

            while (true)
            {
                int menuItem = RunMainMenu();

                if (menuItem == 0)
                {
                    return;
                }

                if (menuItem == 1)
                {
                    RunListYubiKeys();
                    continue;
                }

                if (menuItem == 2)
                {
                    RunChooseYubiKey();
                    continue;
                }

                int exampleIndex = menuItem - 3;
                if (exampleIndex >= 0 && exampleIndex < _examples.Length)
                {
                    // If the caller has chosen one specifically (they ran
                    // ChooseYubiKey), use it. If not, pick a default.
                    if (DefaultChooseYubiKey())
                    {
                        RunExample(_examples[exampleIndex]);
                    }
                }
                else
                {
                    WriteSpecial("Invalid response for this menu.");
                }
            }
        }

        // ─── Main Menu ──────────────────────────────────────────────

        private int RunMainMenu()
        {
            Console.WriteLine("\nYubiKey 5.8 FIDO2 Quickstarts");
            Console.WriteLine("What do you want to do?");
            Console.WriteLine(
                "   " + 1.ToString("D1", CultureInfo.InvariantCulture) + " - List YubiKeys");
            Console.WriteLine(
                "   " + 2.ToString("D1", CultureInfo.InvariantCulture) + " - Choose YubiKey");

            for (int i = 0; i < _examples.Length; i++)
            {
                Console.WriteLine(
                    "   " + (i + 3).ToString("D1", CultureInfo.InvariantCulture) +
                    " - " + _examples[i].Title);
            }

            Console.WriteLine(
                "   " + 0.ToString("D1", CultureInfo.InvariantCulture) + " - Exit");

            string input = Console.ReadLine() ?? "";
            if (int.TryParse(input, out int choice))
            {
                return choice;
            }

            return -1;
        }

        // ─── YubiKey Selection ──────────────────────────────────────

        // List all currently connected YubiKeys.
        private static void RunListYubiKeys()
        {
            var keys = YubiKeyDevice.FindAll().ToArray();

            if (keys.Length == 0)
            {
                WriteSpecial("No YubiKeys found");
                return;
            }

            string outputList = "\n   YubiKeys:";
            foreach (var key in keys)
            {
                string serial = key.SerialNumber?.ToString() ?? "No serial number";
                string version = key.FirmwareVersion.ToString();
                outputList += "\n   " + serial + " : " + version;
            }

            outputList += "\n";
            WriteSpecial(outputList);
        }

        // Choose the YubiKey to use. Lists all connected YubiKeys and
        // asks the user to pick one. If only one is connected, selects
        // it automatically.
        private void RunChooseYubiKey()
        {
            var keys = YubiKeyDevice.FindAll().ToArray();

            if (keys.Length == 0)
            {
                WriteSpecial("No YubiKeys found");
                return;
            }

            if (keys.Length == 1)
            {
                if (keys[0].FirmwareVersion < MinimumFirmware)
                {
                    WriteSpecial(
                        "YubiKey " + keys[0].FirmwareVersion +
                        " does not meet the minimum firmware (" + MinimumFirmware +
                        "). These quickstarts require YubiKey 5.8 or later.");
                    return;
                }

                _yubiKeyChosen = keys[0];
                _chosenByUser = true;
                string serial = keys[0].SerialNumber?.ToString() ?? "No serial number";
                Console.WriteLine(
                    "\n   Using YubiKey " + keys[0].FirmwareVersion + " : " + serial + "\n");
                return;
            }

            Console.WriteLine("Which YubiKey do you want to use?");
            string[] choices = new string[keys.Length];
            for (int i = 0; i < keys.Length; i++)
            {
                string serial = keys[i].SerialNumber?.ToString() ?? "No serial number";
                choices[i] = keys[i].FirmwareVersion + " : " + serial;
                Console.WriteLine(
                    "   " + (i + 1).ToString("D1", CultureInfo.InvariantCulture) +
                    " - " + choices[i]);
            }

            string input = Console.ReadLine() ?? "";
            if (int.TryParse(input, out int choice)
                && choice >= 1 && choice <= keys.Length)
            {
                if (keys[choice - 1].FirmwareVersion < MinimumFirmware)
                {
                    WriteSpecial(
                        "YubiKey " + keys[choice - 1].FirmwareVersion +
                        " does not meet the minimum firmware (" + MinimumFirmware +
                        "). These quickstarts require YubiKey 5.8 or later.");
                    return;
                }

                _yubiKeyChosen = keys[choice - 1];
                _chosenByUser = true;
                return;
            }

            WriteSpecial("Invalid response");
        }

        // Make sure a YubiKey is chosen.
        // If the user has already chosen a YubiKey, don't do anything.
        // If not, pick a default. If there's only one, use it. If there
        // are more than one, ask the user to choose.
        private bool DefaultChooseYubiKey()
        {
            if (_chosenByUser && !(_yubiKeyChosen is null))
            {
                return true;
            }

            var keys = YubiKeyDevice.FindAll().ToArray();

            if (keys.Length == 0)
            {
                WriteSpecial("No YubiKeys found");
                return false;
            }

            if (keys.Length == 1)
            {
                if (keys[0].FirmwareVersion < MinimumFirmware)
                {
                    WriteSpecial(
                        "YubiKey " + keys[0].FirmwareVersion +
                        " does not meet the minimum firmware (" + MinimumFirmware +
                        "). These quickstarts require YubiKey 5.8 or later.");
                    return false;
                }

                _yubiKeyChosen = keys[0];
                return true;
            }

            RunChooseYubiKey();
            return !(_yubiKeyChosen is null);
        }

        // ─── Example Execution ──────────────────────────────────────

        private void RunExample(IQuickstart example)
        {
            WriteSpecial(example.Title + " - " + example.Description);
            Console.WriteLine("  Firmware: " + _yubiKeyChosen!.FirmwareVersion + "\n");

            try
            {
                example.Run(_yubiKeyChosen, _keyCollector.Collect);
            }
            catch (Exception ex)
            {
                Console.WriteLine("\n  \u2717 Error: " + ex.Message);
            }

            Console.WriteLine("\nPress any key to return to the menu...");
            Console.ReadKey(true);
        }

        // Write a special message matching the SDK's
        // SampleMenu.WriteMessage(MessageType.Special, ...) format.
        private static void WriteSpecial(string message) =>
            Console.WriteLine("\n---" + message + "---\n");
    }
}
